package org.libreoffice.androidlib.ai;

public class ArticleTemplate {
    public final String key;
    public final String category;
    public final String subTypeLabel;
    public final String generateText;
    public final String promptTemplate;
    public final Variable[] variables;

    public ArticleTemplate(String key, String category, String subTypeLabel, String generateText,
            String promptTemplate, Variable[] variables) {
        this.key = key;
        this.category = category;
        this.subTypeLabel = subTypeLabel;
        this.generateText = generateText;
        this.promptTemplate = promptTemplate;
        this.variables = variables;
    }

    public static class Variable {
        public final String label;
        public final String hint;

        public Variable(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }
}
