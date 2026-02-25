package analysis;

public record TradeTicket(
    String symbol,
    TradeSide side,
    int quantity,
    double price,
    String note,
    String storyId,
    String storyTitle
) {
}
