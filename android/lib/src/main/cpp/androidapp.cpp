/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

#include <config.h>

#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <atomic>
#include <chrono>
#include <mutex>
#include <thread>
#include <vector>
#include <condition_variable>

#include <FakeSocket.hpp>
#include <Kit.hpp>
#include <Log.hpp>
#include <COOLWSD.hpp>
#include <Protocol.hpp>
#include <SetupKitEnvironment.hpp>
#include <Util.hpp>

#include <osl/detail/android-bootstrap.h>

#include <unistd.h>

const int SHOW_JS_MAXLEN = 70;

int coolwsd_server_socket_fd = -1;

const char* user_name;

static std::string fileURL;
static int fakeClientFd = -1;
static int closeNotificationPipeForForwardingThread[2] = {-1, -1};
static JavaVM* javaVM = nullptr;
static bool lokInitialized = false;
static std::atomic<bool> g_coolwsdThreadRunning{false};
static std::mutex g_coolwsdStartMutex;
static std::atomic<bool> g_coolwsdReuseAwaitingHullO{false};
static std::atomic<int> g_coolwsdSessionGeneration{0};
static std::atomic<unsigned> g_mobileAppDocIdCounter{1};
static unsigned g_currentMobileAppDocId = 0;
static std::atomic<int> g_hulloConnectedSession{-1};
static std::atomic<bool> g_app2jsRunning{false};
static std::mutex g_serverListenMutex;
static std::condition_variable g_serverListenCv;
static std::mutex g_app2jsMutex;
static std::condition_variable g_app2jsCv;

// Remember the reference to the LOActivity
jclass g_loActivityClz = nullptr;
jobject g_loActivityObj = nullptr;
static bool g_javaCallbacksEnabled = false;

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void*) {
    javaVM = vm;
    libreofficekit_set_javavm(vm);

    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR; // JNI version not supported.
    }


    // Uncomment the following to see the logs from the core too
    //setenv("SAL_LOG", "+WARN+INFO", 0);
#if ENABLE_DEBUG
    Log::initialize("Mobile", "debug", false, false, {}, false, {});
#else
    Log::initialize("Mobile", "information", false, false, {}, false, {});
#endif
    return JNI_VERSION_1_6;
}

// Exception safe JVM detach, JNIEnv is TLS for Java - so per-thread.
class JNIThreadContext
{
    JNIEnv *_env;
public:
    JNIThreadContext()
    {
        assert(javaVM != nullptr);
        jint res = javaVM->GetEnv((void**)&_env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            LOG_DBG("Attach worker thread");
            res = javaVM->AttachCurrentThread(&_env, nullptr);
            if (JNI_OK != res) {
                LOG_DBG("Failed to AttachCurrentThread");
            }
        }
        else if (res == JNI_EVERSION) {
            LOG_DBG("GetEnv version not supported");
            return;
        }
        else if (res != JNI_OK) {
            LOG_DBG("GetEnv another error " << res);
            return;
        }
    }

    ~JNIThreadContext()
    {
        javaVM->DetachCurrentThread();
    }

    JNIEnv *getEnv() const { return _env; }
};

static void send2JS(const JNIThreadContext &jctx, const std::vector<char>& buffer)
{
    if (!buffer.empty())
    {
        const size_t probeLen = buffer.size() < 96 ? buffer.size() : 96;
        const std::string probe(buffer.data(), probeLen);
        if (probe.find("BYE") != std::string::npos || probe.find("close") != std::string::npos)
        {
            __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                "exit_probe_send2JS msg=%.96s callbacks=%d pid=%d",
                                probe.c_str(), g_javaCallbacksEnabled ? 1 : 0, getpid());
        }
        if (probe.rfind("progress:", 0) == 0 || probe.rfind("error:", 0) == 0)
        {
            __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                "wsd2js session=%d appDocId=%u msg=%.96s pid=%d",
                                g_coolwsdSessionGeneration.load(), g_currentMobileAppDocId,
                                probe.c_str(), getpid());
        }
    }
    if (!g_javaCallbacksEnabled || g_loActivityObj == nullptr || g_loActivityClz == nullptr) {
        return;
    }
    LOG_DBG("Send to JS: " << COOLProtocol::getAbbreviatedMessage(buffer.data(), buffer.size()));

    JNIEnv *env = jctx.getEnv();

    jbyteArray jmessage = env->NewByteArray(buffer.size());
    env->SetByteArrayRegion(jmessage, 0, buffer.size(),
                            reinterpret_cast<const jbyte *>(buffer.data()));

    jmethodID callFakeWebsocket = env->GetMethodID(g_loActivityClz, "rawCallFakeWebsocketOnMessage", "([B)V");
    env->CallVoidMethod(g_loActivityObj, callFakeWebsocket, jmessage);
    env->DeleteLocalRef(jmessage);

    if (env->ExceptionCheck())
        env->ExceptionDescribe();
}

void postDirectMessage(std::string message)
{
    const bool isBye = (message.rfind("BYE", 0) == 0);
    const bool isSaveMsg = (message.rfind("SAVE", 0) == 0);
    if (isBye || message.rfind("close", 0) == 0 || isSaveMsg)
    {
        __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                            "exit_probe_postDirectMessage msg=%.96s callbacks=%d hasObj=%d pid=%d",
                            message.c_str(), g_javaCallbacksEnabled ? 1 : 0,
                            g_loActivityObj != nullptr ? 1 : 0, getpid());
    }
    if (!g_javaCallbacksEnabled || g_loActivityObj == nullptr || g_loActivityClz == nullptr) {
        if (isBye || isSaveMsg)
        {
            __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                "exit_probe_postDirectMessage_blocked reason=callbacks_disabled msg=%.32s pid=%d",
                                message.c_str(), getpid());
        }
        return;
    }
    JNIThreadContext ctx;
    JNIEnv *env = ctx.getEnv();

    jstring jstr = env->NewStringUTF(message.c_str());
    jmethodID callPostMobileMessage = env->GetMethodID(g_loActivityClz, "postMobileMessage", "(Ljava/lang/String;)V");
    env->CallVoidMethod(g_loActivityObj, callPostMobileMessage, jstr);
    env->DeleteLocalRef(jstr);

    if (env->ExceptionCheck())
        env->ExceptionDescribe();
}

/// Close the document session (disconnect client). Matches iOS/gtk/wasm: only close
/// the notification pipe; do NOT block on lokit_main_mutex / coolwsdRunningMutex —
/// that deadlocks or kills the process on Impress exit (log stops at closeDocument_start).
void closeDocument()
{
    __android_log_print(ANDROID_LOG_INFO, "LOActivity", "closeDocument_start pid=%d", getpid());
    if (closeNotificationPipeForForwardingThread[0] >= 0)
    {
        fakeSocketClose(closeNotificationPipeForForwardingThread[0]);
        closeNotificationPipeForForwardingThread[0] = -1;
    }
    __android_log_print(ANDROID_LOG_INFO, "LOActivity", "closeDocument_done pid=%d", getpid());
}

static void runCoolwsdThread()
{
    char* argv[2];
    argv[0] = strdup("mobile");
    argv[1] = nullptr;
    Util::setThreadName("app");
    __android_log_print(ANDROID_LOG_INFO, "LOActivity", "coolwsd_thread_start pid=%d", getpid());
    std::unique_ptr<COOLWSD> coolwsd = std::make_unique<COOLWSD>();
    coolwsd->run(1, argv);
    coolwsd_server_socket_fd = -1;
    {
        std::lock_guard<std::mutex> lock(g_serverListenMutex);
        g_serverListenCv.notify_all();
    }
    g_coolwsdThreadRunning.store(false);
    __android_log_print(ANDROID_LOG_ERROR, "LOActivity",
                        "coolwsd_run_unexpected_return pid=%d", getpid());
}

static void ensureCoolwsdThreadRunning()
{
    if (g_coolwsdThreadRunning.load())
        return;
    std::lock_guard<std::mutex> lock(g_coolwsdStartMutex);
    if (g_coolwsdThreadRunning.load())
        return;
    g_coolwsdThreadRunning.store(true);
    std::thread(runCoolwsdThread).detach();
}

static bool waitForServerListening(int timeoutMs)
{
    std::unique_lock<std::mutex> lock(g_serverListenMutex);
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while ((coolwsd_server_socket_fd < 0 || !g_coolwsdThreadRunning.load())
           && std::chrono::steady_clock::now() < deadline)
        g_serverListenCv.wait_until(lock, deadline);
    return coolwsd_server_socket_fd >= 0 && g_coolwsdThreadRunning.load();
}

/// fakeSocketConnect blocks until accept; run on a helper thread with a timeout.
static int fakeSocketConnectWithTimeout(int clientFd, int serverFd, int timeoutMs)
{
    std::atomic<int> result{-2};
    std::thread connectThread([clientFd, serverFd, &result] {
        result.store(fakeSocketConnect(clientFd, serverFd));
    });
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while (result.load() == -2 && std::chrono::steady_clock::now() < deadline)
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    if (result.load() == -2)
    {
        connectThread.detach();
        return -2;
    }
    connectThread.join();
    return result.load();
}

static bool waitForApp2jsIdleImpl(int timeoutMs)
{
    std::unique_lock<std::mutex> lock(g_app2jsMutex);
    return g_app2jsCv.wait_for(lock, std::chrono::milliseconds(timeoutMs),
                               [] { return !g_app2jsRunning.load(); });
}

static void markApp2jsStopped()
{
    g_app2jsRunning = false;
    g_app2jsCv.notify_all();
}

extern "C" void androidLogCoolwsdDiag(const char* phase, const char* detail)
{
    __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                        "coolwsd_diag phase=%s detail=%s pid=%d", phase, detail, getpid());
}

/// Legacy JNI hook; COOLWSD no longer restarts per document (iOS model).
extern "C" JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_setExpectCoolwsdRun(JNIEnv*, jobject, jboolean expect)
{
    __android_log_print(ANDROID_LOG_INFO, "LOActivity", "expect_coolwsd_run=%s (no-op ios_model)",
                        (expect == JNI_TRUE) ? "true" : "false");
}

/// Wait until app2js forwarding thread has exited after BYE.
extern "C" JNIEXPORT jboolean JNICALL
Java_org_libreoffice_androidlib_LOActivity_waitForApp2jsIdle(JNIEnv*, jobject, jint timeoutMs)
{
    const int ms = timeoutMs > 0 ? timeoutMs : 8000;
    const bool ok = waitForApp2jsIdleImpl(ms);
    __android_log_print(ANDROID_LOG_INFO, "LOActivity", "wait_app2js_idle=%s pid=%d",
                        ok ? "true" : "false", getpid());
    return ok ? JNI_TRUE : JNI_FALSE;
}

/// Kept for binary compat; maps to waitForApp2jsIdle under iOS model.
extern "C" JNIEXPORT jboolean JNICALL
Java_org_libreoffice_androidlib_LOActivity_waitForCoolwsdRunIdle(JNIEnv* env, jobject obj,
                                                                 jint timeoutMs)
{
    return Java_org_libreoffice_androidlib_LOActivity_waitForApp2jsIdle(env, obj, timeoutMs);
}

void androidNotifyCoolwsdServerListening()
{
    __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                        "coolwsd_server_listening fd=%d session=%d pid=%d",
                        coolwsd_server_socket_fd, g_coolwsdSessionGeneration.load(), getpid());
    g_serverListenCv.notify_all();
}

void androidNotifyCoolwsdServerStopped()
{
    coolwsd_server_socket_fd = -1;
    __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                        "coolwsd_server_stopped pid=%d", getpid());
    g_serverListenCv.notify_all();
}

/// Drop JNI callbacks to LOActivity after WebView is destroyed (avoid use-after-destroy crashes).
extern "C" JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_clearNativeActivityCallbacks(JNIEnv* env, jobject)
{
    g_javaCallbacksEnabled = false;
    if (g_loActivityObj != nullptr) {
        env->DeleteGlobalRef(g_loActivityObj);
        g_loActivityObj = nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, "LOActivity", "native_activity_callbacks_cleared pid=%d", getpid());
}

/// Handle a message from JavaScript.
extern "C" JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_postMobileMessageNative(JNIEnv *env, jobject, jstring message)
{
    const char *string_value = env->GetStringUTFChars(message, nullptr);

    if (string_value)
    {
        LOG_DBG("From JS: cool: " << string_value);

        if (strcmp(string_value, "HULLO") == 0)
        {
            const int session = g_coolwsdSessionGeneration.load();
            const bool isReuse = lokInitialized && g_coolwsdReuseAwaitingHullO.exchange(false);
            if (isReuse)
            {
                __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                    "hullo_reuse_start clientFd=%d session=%d serverFd=%d pid=%d",
                                    fakeClientFd, session, coolwsd_server_socket_fd, getpid());
            }

            if (g_hulloConnectedSession.load() == session)
            {
                __android_log_print(ANDROID_LOG_WARN, "LOActivity",
                                    "hullo_ignore_duplicate session=%d pid=%d", session, getpid());
                env->ReleaseStringUTFChars(message, string_value);
                return;
            }

            if (!waitForServerListening(8000))
            {
                __android_log_print(ANDROID_LOG_WARN, "LOActivity",
                                    "hullo_wait_server_timeout session=%d clientFd=%d pid=%d",
                                    session, fakeClientFd, getpid());
                env->ReleaseStringUTFChars(message, string_value);
                return;
            }

            const int currentFakeClientFd = fakeClientFd;
            if (currentFakeClientFd < 0)
            {
                __android_log_print(ANDROID_LOG_WARN, "LOActivity",
                                    "hullo_invalid_client_fd session=%d pid=%d", session, getpid());
                env->ReleaseStringUTFChars(message, string_value);
                return;
            }

            const int rc = fakeSocketConnectWithTimeout(currentFakeClientFd, coolwsd_server_socket_fd, 8000);
            if (rc == -2)
            {
                __android_log_print(ANDROID_LOG_WARN, "LOActivity",
                                    "hullo_connect_timeout clientFd=%d serverFd=%d session=%d pid=%d",
                                    currentFakeClientFd, coolwsd_server_socket_fd, session, getpid());
                env->ReleaseStringUTFChars(message, string_value);
                return;
            }
            if (rc == -1)
            {
                __android_log_print(ANDROID_LOG_ERROR, "LOActivity",
                                    "hullo_connect_fail clientFd=%d serverFd=%d errno=%d session=%d pid=%d",
                                    currentFakeClientFd, coolwsd_server_socket_fd, errno, session,
                                    getpid());
                env->ReleaseStringUTFChars(message, string_value);
                return;
            }

            g_hulloConnectedSession.store(session);
            __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                "hullo_connect_ok clientFd=%d serverFd=%d session=%d pid=%d",
                                currentFakeClientFd, coolwsd_server_socket_fd, session, getpid());

            closeNotificationPipeForForwardingThread[0] = -1;
            closeNotificationPipeForForwardingThread[1] = -1;
            fakeSocketPipe2(closeNotificationPipeForForwardingThread);

            g_app2jsRunning = true;
            std::thread([currentFakeClientFd]
                        {
                            Util::setThreadName("app2js");
                            JNIThreadContext ctx;
                            while (true)
                            {
                               struct pollfd pollfd[2];
                               pollfd[0].fd = currentFakeClientFd;
                               pollfd[0].events = POLLIN;
                               pollfd[1].fd = closeNotificationPipeForForwardingThread[1];
                               pollfd[1].events = POLLIN;
                               const int pollResult = fakeSocketPoll(pollfd, 2, -1);
                               if (pollResult > 0)
                               {
                                   if (pollfd[1].revents == POLLIN)
                                   {
                                       __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                                           "exit_diag_app2js reason=bye_pipe fd=%d pid=%d",
                                                           currentFakeClientFd, getpid());
                                       fakeSocketClose(closeNotificationPipeForForwardingThread[1]);
                                       closeNotificationPipeForForwardingThread[1] = -1;
                                       fakeSocketClose(currentFakeClientFd);
                                       __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                                           "exit_diag_app2js thread_exit pid=%d", getpid());
                                       markApp2jsStopped();
                                       return;
                                   }
                                   if (pollfd[0].revents == POLLIN)
                                   {
                                       int n = fakeSocketAvailableDataLength(currentFakeClientFd);
                                       if (n == 0)
                                       {
                                           __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                                               "exit_diag_app2js reason=client_eof fd=%d pid=%d",
                                                               currentFakeClientFd, getpid());
                                           markApp2jsStopped();
                                           return;
                                       }
                                       std::vector<char> buf(n);
                                       n = fakeSocketRead(currentFakeClientFd, buf.data(), n);
                                       send2JS(ctx, buf);
                                   }
                               }
                               else
                               {
                                   __android_log_print(ANDROID_LOG_WARN, "LOActivity",
                                                       "exit_diag_app2js reason=poll_error result=%d fd=%d pid=%d",
                                                       pollResult, currentFakeClientFd, getpid());
                                   markApp2jsStopped();
                                   return;
                               }
                           }
                        }).detach();

            LOG_DBG("Actually sending to Online:" << fileURL);
            const std::string hullOMessage = fileURL + " " + std::to_string(g_currentMobileAppDocId);
            __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                                "hullo_send_url session=%d appDocId=%u bytes=%zu pid=%d",
                                session, g_currentMobileAppDocId, hullOMessage.size(), getpid());
            fakeSocketWriteQueue(currentFakeClientFd, hullOMessage.c_str(), hullOMessage.size());
        }
        else if (strcmp(string_value, "BYE") == 0)
        {
            LOG_DBG("Document window terminating on JavaScript side. Closing our end of the socket.");

            closeDocument();
        }
        else
        {
            // Send the message to COOLWSD
            fakeSocketWriteQueue(fakeClientFd, string_value, strlen(string_value));
        }
    }
    else
        LOG_DBG("From JS: cool: some object");
}

extern "C" jboolean libreofficekit_initialize(JNIEnv* env, jstring dataDir, jstring cacheDir, jstring apkFile, jobject assetManager);

/// Create the COOLWSD instance.
extern "C" JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_createCOOLWSD(JNIEnv *env, jobject instance, jstring dataDir, jstring cacheDir, jstring apkFile, jobject assetManager, jstring loadFileURL, jstring uiMode, jstring userName)
{
    fileURL = std::string(env->GetStringUTFChars(loadFileURL, nullptr));

    // remember the LOActivity class and object to be able to call back
    env->DeleteGlobalRef(g_loActivityClz);
    env->DeleteGlobalRef(g_loActivityObj);

    jclass clz = env->GetObjectClass(instance);
    g_loActivityClz = (jclass) env->NewGlobalRef(clz);
    g_loActivityObj = env->NewGlobalRef(instance);
    g_javaCallbacksEnabled = true;

    // already initialized?
    if (lokInitialized)
    {
        g_javaCallbacksEnabled = false;
        __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                            "createCOOLWSD_reuse close_previous_doc pid=%d", getpid());
        closeDocument();
        waitForApp2jsIdleImpl(8000);
        ensureCoolwsdThreadRunning();
        g_currentMobileAppDocId = g_mobileAppDocIdCounter++;
        fakeClientFd = fakeSocketSocket();
        g_coolwsdSessionGeneration++;
        g_hulloConnectedSession.store(-1);
        g_coolwsdReuseAwaitingHullO = true;
        __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                            "createCOOLWSD_reuse await_hullo session=%d appDocId=%u clientFd=%d serverFd=%d pid=%d",
                            g_coolwsdSessionGeneration.load(), g_currentMobileAppDocId, fakeClientFd,
                            coolwsd_server_socket_fd, getpid());
        g_javaCallbacksEnabled = true;
        return;
    }
    const std::string userInterfaceMode = std::string(env->GetStringUTFChars(uiMode, nullptr));
    setupKitEnvironment(userInterfaceMode);
    static const std::string userNameString = std::string(env->GetStringUTFChars(userName, nullptr));
    user_name = userNameString.c_str();
    lokInitialized = true;
    libreofficekit_initialize(env, dataDir, cacheDir, apkFile, assetManager);

    Util::setThreadName("main");

    fakeSocketSetLoggingCallback([](const std::string& line)
                                 {
                                     LOG_DBG(line);
                                 });

    fakeClientFd = fakeSocketSocket();
    g_currentMobileAppDocId = g_mobileAppDocIdCounter++;
    g_coolwsdSessionGeneration++;
    g_hulloConnectedSession.store(-1);
    __android_log_print(ANDROID_LOG_INFO, "LOActivity",
                        "createCOOLWSD_first session=%d appDocId=%u clientFd=%d pid=%d",
                        g_coolwsdSessionGeneration.load(), g_currentMobileAppDocId, fakeClientFd,
                        getpid());
    ensureCoolwsdThreadRunning();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_saveAs(JNIEnv *env, jobject,
                                                  jstring fileUri_, jstring format_,
                                                  jstring options_) {
    const char *fileUri = env->GetStringUTFChars(fileUri_, 0);
    const char *format = env->GetStringUTFChars(format_, 0);
    const char *options = nullptr;
    if (options_ != nullptr)
        options = env->GetStringUTFChars(options_, 0);

    getLOKDocumentForAndroidOnly()->saveAs(fileUri, format, options);

    env->ReleaseStringUTFChars(fileUri_, fileUri);
    env->ReleaseStringUTFChars(format_, format);
    if (options_ != nullptr)
        env->ReleaseStringUTFChars(options_, options);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_postUnoCommand(JNIEnv* pEnv, jobject,
                                                          jstring command, jstring arguments, jboolean bNotifyWhenFinished)
{
    const char* pCommand = pEnv->GetStringUTFChars(command, nullptr);
    const char* pArguments = nullptr;
    if (arguments != nullptr)
        pArguments = pEnv->GetStringUTFChars(arguments, nullptr);

    getLOKDocumentForAndroidOnly()->postUnoCommand(pCommand, pArguments, bNotifyWhenFinished);

    pEnv->ReleaseStringUTFChars(command, pCommand);
    if (arguments != nullptr)
        pEnv->ReleaseStringUTFChars(arguments, pArguments);
}

static jstring tojstringAndFree(JNIEnv *env, char *str)
{
    if (!str)
        return env->NewStringUTF("");
    jstring ret = env->NewStringUTF(str);
    free(str);
    return ret;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_libreoffice_androidlib_LOActivity_getTextSelection(JNIEnv* pEnv, jobject, jstring mimeType)
{
    if (!getLOKDocumentForAndroidOnly())
        return pEnv->NewStringUTF("");

    const char* pMimeType = pEnv->GetStringUTFChars(mimeType, nullptr);
    char* text = getLOKDocumentForAndroidOnly()->getTextSelection(pMimeType, nullptr);
    pEnv->ReleaseStringUTFChars(mimeType, pMimeType);

    return tojstringAndFree(pEnv, text);
}

const char* copyJavaString(JNIEnv* pEnv, jstring aJavaString)
{
    const char* pTemp = pEnv->GetStringUTFChars(aJavaString, nullptr);
    const char* pClone = strdup(pTemp);
    pEnv->ReleaseStringUTFChars(aJavaString, pTemp);
    return pClone;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_libreoffice_androidlib_LOActivity_getClipboardContent(JNIEnv *env, jobject, jobject lokClipboardData)
{
    const char** mimeTypes = nullptr;
    size_t outCount = 0;
    char  **outMimeTypes = nullptr;
    size_t *outSizes = nullptr;
    char  **outStreams = nullptr;
    bool bResult = false;

    jclass jclazz = env->FindClass("java/util/ArrayList");
    jmethodID methodId_ArrayList_Add = env->GetMethodID(jclazz, "add", "(Ljava/lang/Object;)Z");

    jclass class_LokClipboardEntry = env->FindClass("org/libreoffice/androidlib/lok/LokClipboardEntry");
    jmethodID methodId_LokClipboardEntry_Constructor = env->GetMethodID(class_LokClipboardEntry, "<init>", "()V");
    jfieldID fieldId_LokClipboardEntry_Mime = env->GetFieldID(class_LokClipboardEntry , "mime", "Ljava/lang/String;");
    jfieldID fieldId_LokClipboardEntry_Data = env->GetFieldID(class_LokClipboardEntry, "data", "[B");

    jclass class_LokClipboardData = env->GetObjectClass(lokClipboardData);
    jfieldID fieldId_LokClipboardData_clipboardEntries = env->GetFieldID(class_LokClipboardData , "clipboardEntries", "Ljava/util/ArrayList;");

    if (getLOKDocumentForAndroidOnly()->getClipboard(mimeTypes,
                                                     &outCount, &outMimeTypes,
                                                     &outSizes, &outStreams))
    {
        // return early
        if (outCount == 0)
            return bResult;

        for (size_t i = 0; i < outCount; ++i)
        {
            // Create new LokClipboardEntry instance
            jobject clipboardEntry = env->NewObject(class_LokClipboardEntry, methodId_LokClipboardEntry_Constructor);

            jstring mimeType = tojstringAndFree(env, outMimeTypes[i]);
            // clipboardEntry.mime= mimeType
            env->SetObjectField(clipboardEntry, fieldId_LokClipboardEntry_Mime, mimeType);
            env->DeleteLocalRef(mimeType);

            size_t aByteArraySize = outSizes[i];
            jbyteArray aByteArray = env->NewByteArray(aByteArraySize);
            // Copy char* to bytearray
            env->SetByteArrayRegion(aByteArray, 0, aByteArraySize, (jbyte*) outStreams[i]);
            // clipboardEntry.data = aByteArray
            env->SetObjectField(clipboardEntry, fieldId_LokClipboardEntry_Data, aByteArray);

            // clipboardData.clipboardEntries
            jobject lokClipboardData_clipboardEntries = env->GetObjectField(lokClipboardData, fieldId_LokClipboardData_clipboardEntries);

            // clipboardEntries.add(clipboardEntry)
            env->CallBooleanMethod(lokClipboardData_clipboardEntries, methodId_ArrayList_Add, clipboardEntry);
        }
        bResult = true;
    }
    else
        LOG_DBG("failed to fetch mime-types");

    const char* mimeTypesHTML[] = { "text/plain;charset=utf-8", "text/html", nullptr };

    if (getLOKDocumentForAndroidOnly()->getClipboard(mimeTypesHTML,
                                                     &outCount, &outMimeTypes,
                                                     &outSizes, &outStreams))
    {
        // return early
        if (outCount == 0)
            return bResult;

        for (size_t i = 0; i < outCount; ++i)
        {
            // Create new LokClipboardEntry instance
            jobject clipboardEntry = env->NewObject(class_LokClipboardEntry, methodId_LokClipboardEntry_Constructor);

            jstring mimeType = tojstringAndFree(env, outMimeTypes[i]);
            // clipboardEntry.mime= mimeType
            env->SetObjectField(clipboardEntry, fieldId_LokClipboardEntry_Mime, mimeType);
            env->DeleteLocalRef(mimeType);

            size_t aByteArraySize = outSizes[i];
            jbyteArray aByteArray = env->NewByteArray(aByteArraySize);
            // Copy char* to bytearray
            env->SetByteArrayRegion(aByteArray, 0, aByteArraySize, (jbyte*) outStreams[i]);
            // clipboardEntry.data = aByteArray
            env->SetObjectField(clipboardEntry, fieldId_LokClipboardEntry_Data, aByteArray);

            // clipboardData.clipboardEntries
            jobject lokClipboardData_clipboardEntries = env->GetObjectField(lokClipboardData, fieldId_LokClipboardData_clipboardEntries);

            // clipboardEntries.add(clipboardEntry)
            env->CallBooleanMethod(lokClipboardData_clipboardEntries, methodId_ArrayList_Add, clipboardEntry);
        }
        bResult = true;
    }
    else
        LOG_DBG("failed to fetch mime-types");

    return bResult;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_setClipboardContent(JNIEnv *env, jobject, jobject lokClipboardData) {
    jclass class_ArrayList= env->FindClass("java/util/ArrayList");
    jmethodID methodId_ArrayList_ToArray = env->GetMethodID(class_ArrayList, "toArray", "()[Ljava/lang/Object;");

    jclass class_LokClipboardEntry = env->FindClass("org/libreoffice/androidlib/lok/LokClipboardEntry");
    jfieldID fieldId_LokClipboardEntry_Mime = env->GetFieldID(class_LokClipboardEntry , "mime", "Ljava/lang/String;");
    jfieldID fieldId_LokClipboardEntry_Data = env->GetFieldID(class_LokClipboardEntry, "data", "[B");

    jclass class_LokClipboardData = env->GetObjectClass(lokClipboardData);
    jfieldID fieldId_LokClipboardData_clipboardEntries = env->GetFieldID(class_LokClipboardData , "clipboardEntries", "Ljava/util/ArrayList;");

    jobject lokClipboardData_clipboardEntries = env->GetObjectField(lokClipboardData, fieldId_LokClipboardData_clipboardEntries);

    jobjectArray clipboardEntryArray = (jobjectArray) env->CallObjectMethod(lokClipboardData_clipboardEntries, methodId_ArrayList_ToArray);

    size_t nEntrySize= env->GetArrayLength(clipboardEntryArray);

    if (nEntrySize == 0)
        return;

    std::vector<size_t> pSizes(nEntrySize);
    std::vector<const char*> pMimeTypes(nEntrySize);
    std::vector<const char*> pStreams(nEntrySize);

    for (size_t nEntryIndex = 0; nEntryIndex < nEntrySize; ++nEntryIndex)
    {
        jobject clipboardEntry = env->GetObjectArrayElement(clipboardEntryArray, nEntryIndex);

        jstring mimetype = (jstring) env->GetObjectField(clipboardEntry, fieldId_LokClipboardEntry_Mime);
        jbyteArray data = (jbyteArray) env->GetObjectField(clipboardEntry, fieldId_LokClipboardEntry_Data);

        pMimeTypes[nEntryIndex] = copyJavaString(env, mimetype);

        size_t dataArrayLength = env->GetArrayLength(data);
        char* dataArray = new char[dataArrayLength];
        env->GetByteArrayRegion(data, 0, dataArrayLength, reinterpret_cast<jbyte*>(dataArray));

        pSizes[nEntryIndex] = dataArrayLength;
        pStreams[nEntryIndex] = dataArray;
    }

    getLOKDocumentForAndroidOnly()->setClipboard(nEntrySize, pMimeTypes.data(), pSizes.data(), pStreams.data());
}

extern "C"
JNIEXPORT void JNICALL
Java_org_libreoffice_androidlib_LOActivity_paste(JNIEnv *env, jobject, jstring inMimeType, jbyteArray inData) {
    const char* mimeType = env->GetStringUTFChars(inMimeType, nullptr);

    size_t dataArrayLength = env->GetArrayLength(inData);
    char* dataArray = new char[dataArrayLength];
    env->GetByteArrayRegion(inData, 0, dataArrayLength, reinterpret_cast<jbyte*>(dataArray));
    getLOKDocumentForAndroidOnly()->paste(mimeType, dataArray, dataArrayLength);
    env->ReleaseStringUTFChars(inMimeType, mimeType);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_libreoffice_androidlib_COWebViewClient_getEmbeddedMediaPath(JNIEnv *env, jobject, jstring inTag) {
    std::string tag = copyJavaString(env, inTag);
    std::string mediaPath = getDocumentBrokerForAndroidOnly()->getEmbeddedMediaPath(tag);
    return env->NewStringUTF(mediaPath.c_str());
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab cinoptions=b1,g0,N-s cinkeys+=0=break: */
