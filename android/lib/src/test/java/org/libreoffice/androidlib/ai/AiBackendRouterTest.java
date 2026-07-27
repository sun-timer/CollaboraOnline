package org.libreoffice.androidlib.ai;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AiBackendRouterTest {
    private final AiBackendRouter router = new AiBackendRouter();

    private static AiBackendRouter.LocalModelState readyState() {
        return new AiBackendRouter.LocalModelState(true, true, true);
    }

    @Test
    public void textTaskWithLocalReady_usesLocal() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_CHAT, "base", 0, readyState());
        assertEquals(AiBackend.BACKEND_LOCAL, route.backend);
    }

    @Test
    public void polishWithLocalReady_usesLocal() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_POLISH, "base", 0, readyState());
        assertEquals(AiBackend.BACKEND_LOCAL, route.backend);
        assertEquals("local_ready", route.reason);
    }

    @Test
    public void outlineWithLocalReady_usesLocal() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_OUTLINE, "base", 0, readyState());
        assertEquals(AiBackend.BACKEND_LOCAL, route.backend);
        assertEquals("local_ready", route.reason);
    }

    @Test
    public void continueWriteWithLocalReady_usesLocal() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_CONTINUE, "base", 0, readyState());
        assertEquals(AiBackend.BACKEND_LOCAL, route.backend);
        assertEquals("local_ready", route.reason);
    }

    @Test
    public void impressGenerateWithLocalReady_usesLocal() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_IMPRESS_GENERATE, "base", 0, readyState());
        assertEquals(AiBackend.BACKEND_LOCAL, route.backend);
        assertEquals("local_ready", route.reason);
    }

    @Test
    public void imageTask_alwaysCloud() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_IMAGE_GENERATE, "image", 0, readyState());
        assertEquals(AiBackend.BACKEND_CLOUD, route.backend);
    }

    @Test
    public void docQa_defaultCloudEvenIfLocalReady() {
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_DOC_QA, "base", 1000, readyState());
        assertEquals(AiBackend.BACKEND_CLOUD, route.backend);
        assertEquals("doc_qa_default_cloud", route.reason);
    }

    @Test
    public void localNotReady_fallsBackCloud() {
        AiBackendRouter.LocalModelState state = new AiBackendRouter.LocalModelState(true, false, false);
        AiBackendRouter.ResolvedRoute route = router.resolve(
                AiChatCoordinator.MODE_CHAT, "base", 0, state);
        assertEquals(AiBackend.BACKEND_CLOUD, route.backend);
    }
}
