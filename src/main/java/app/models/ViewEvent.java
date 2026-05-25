package app.models;

public class ViewEvent extends Event {

    private String articleId;

    public ViewEvent() {
    }

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }
}
