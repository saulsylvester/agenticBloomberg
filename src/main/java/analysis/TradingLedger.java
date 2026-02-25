package analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TradingLedger {
    private static final int MAX_RECENT_TRADES = 40;

    private final Object monitor = new Object();
    private final double startingCash;

    private double cash;
    private double realizedPnl;
    private int sequence = 1;
    private final Map<String, PositionState> positionsBySymbol = new HashMap<>();
    private final List<ExecutedTrade> recentTrades = new ArrayList<>();

    public TradingLedger(double startingCash) {
        if (startingCash <= 0.0) {
            throw new IllegalArgumentException("startingCash must be positive");
        }
        this.startingCash = startingCash;
        this.cash = startingCash;
    }

    public TradeExecutionResult execute(TradeTicket ticket) {
        synchronized (monitor) {
            TradeTicket normalized = normalize(ticket);
            double notional = normalized.quantity() * normalized.price();

            PositionState position = positionsBySymbol.get(normalized.symbol());
            if (normalized.side() == TradeSide.BUY) {
                if (notional > cash) {
                    throw new IllegalArgumentException("Insufficient cash for BUY order");
                }
                cash -= notional;
                if (position == null) {
                    positionsBySymbol.put(normalized.symbol(), new PositionState(normalized.quantity(), normalized.price(), normalized.price()));
                } else {
                    double newQuantity = position.quantity + normalized.quantity();
                    double weightedAverage = ((position.averagePrice * position.quantity) + notional) / newQuantity;
                    position.quantity += normalized.quantity();
                    position.averagePrice = weightedAverage;
                    position.lastPrice = normalized.price();
                }
            } else {
                if (position == null || position.quantity < normalized.quantity()) {
                    throw new IllegalArgumentException("Insufficient position for SELL order");
                }
                cash += notional;
                realizedPnl += (normalized.price() - position.averagePrice) * normalized.quantity();

                position.quantity -= normalized.quantity();
                if (position.quantity == 0) {
                    positionsBySymbol.remove(normalized.symbol());
                } else {
                    position.lastPrice = normalized.price();
                }
            }

            double equity = currentEquityLocked();
            double totalPnl = equity - startingCash;
            ExecutedTrade trade = new ExecutedTrade(
                nextTradeId(),
                Instant.now().toString(),
                normalized.symbol(),
                normalized.side(),
                normalized.quantity(),
                normalized.price(),
                round(notional),
                normalized.note(),
                normalized.storyTitle(),
                round(equity),
                round(totalPnl)
            );

            recentTrades.add(0, trade);
            if (recentTrades.size() > MAX_RECENT_TRADES) {
                recentTrades.remove(recentTrades.size() - 1);
            }

            return new TradeExecutionResult(trade, snapshotLocked());
        }
    }

    public PortfolioView snapshot() {
        synchronized (monitor) {
            return snapshotLocked();
        }
    }

    private PortfolioView snapshotLocked() {
        List<PositionView> positions = new ArrayList<>();
        double unrealizedPnl = 0.0;

        for (Map.Entry<String, PositionState> entry : positionsBySymbol.entrySet()) {
            String symbol = entry.getKey();
            PositionState position = entry.getValue();
            double marketValue = position.quantity * position.lastPrice;
            double positionUnrealized = (position.lastPrice - position.averagePrice) * position.quantity;
            unrealizedPnl += positionUnrealized;

            positions.add(new PositionView(
                symbol,
                position.quantity,
                round(position.averagePrice),
                round(position.lastPrice),
                round(marketValue),
                round(positionUnrealized)
            ));
        }

        positions.sort(Comparator.comparing(PositionView::symbol));
        double equity = currentEquityLocked();
        double totalPnl = equity - startingCash;

        List<EquityPoint> timeline = recentTrades.stream()
            .map(trade -> new EquityPoint(trade.timestamp(), trade.equityAfterTrade()))
            .sorted(Comparator.comparing(EquityPoint::timestamp))
            .toList();

        return new PortfolioView(
            round(startingCash),
            round(cash),
            round(equity),
            round(totalPnl),
            round(realizedPnl),
            round(unrealizedPnl),
            List.copyOf(positions),
            List.copyOf(recentTrades),
            List.copyOf(timeline)
        );
    }

    private double currentEquityLocked() {
        double marketValue = 0.0;
        for (PositionState position : positionsBySymbol.values()) {
            marketValue += position.quantity * position.lastPrice;
        }
        return cash + marketValue;
    }

    private TradeTicket normalize(TradeTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Trade request is required");
        }
        String symbol = ticket.symbol() == null ? "" : ticket.symbol().trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (ticket.side() == null) {
            throw new IllegalArgumentException("Trade side is required");
        }
        if (ticket.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (ticket.price() <= 0.0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }

        String note = ticket.note() == null ? "" : ticket.note().trim();
        String storyTitle = ticket.storyTitle() == null ? "" : ticket.storyTitle().trim();

        return new TradeTicket(symbol, ticket.side(), ticket.quantity(), ticket.price(), note, ticket.storyId(), storyTitle);
    }

    private String nextTradeId() {
        String id = "TRD-" + sequence;
        sequence++;
        return id;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class PositionState {
        private int quantity;
        private double averagePrice;
        private double lastPrice;

        private PositionState(int quantity, double averagePrice, double lastPrice) {
            this.quantity = quantity;
            this.averagePrice = averagePrice;
            this.lastPrice = lastPrice;
        }
    }
}
