package org.libreoffice.androidlib;

/**
 * SpellDialog must be dispatched after the function panel finishes dismissing;
 * otherwise LOKit drops the first modal UNO while the sheet animates closed.
 */
public final class FunctionPanelSpellCheckHelper {

    public interface Host {
        void executeUnoCommand(String command);

        void runAfterFunctionPanelDismiss(Runnable action);
    }

    private FunctionPanelSpellCheckHelper() {
    }

    public static boolean needsDeferredUnoAfterPanelDismiss(String unoCommand) {
        if (unoCommand == null || unoCommand.isEmpty()) {
            return false;
        }
        return ".uno:SpellDialog".equals(unoCommand)
                || ".uno:SpellingAndGrammarDialog".equals(unoCommand);
    }

    public static void runPanelActionAndDismiss(Runnable dismissPanel, String unoCommand,
            Runnable hostAction, Host host) {
        Runnable action = () -> {
            if (hostAction != null) {
                hostAction.run();
            } else if (unoCommand != null && !unoCommand.isEmpty()) {
                host.executeUnoCommand(unoCommand);
            }
        };
        dismissPanel.run();
        if (needsDeferredUnoAfterPanelDismiss(unoCommand)) {
            host.runAfterFunctionPanelDismiss(action);
        } else {
            action.run();
        }
    }
}
