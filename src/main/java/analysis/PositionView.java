package analysis;

public record PositionView(
    String symbol,
    int quantity,
    double averagePrice,
    double lastPrice,
    double marketValue,
    double unrealizedPnl
) {
}
