package analysis;

import java.util.List;

public record PortfolioView(
    double startingCash,
    double cash,
    double equity,
    double totalPnl,
    double realizedPnl,
    double unrealizedPnl,
    List<PositionView> positions,
    List<ExecutedTrade> recentTrades,
    List<EquityPoint> equityTimeline
) {
}
