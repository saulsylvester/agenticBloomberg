package analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TradingLedgerTest {
    @Test
    void executesBuyAndSellAndUpdatesPortfolio() {
        TradingLedger ledger = new TradingLedger(10_000.0);

        ledger.execute(new TradeTicket("TEST", TradeSide.BUY, 10, 100.0, "entry", "", ""));
        TradeExecutionResult sellResult = ledger.execute(new TradeTicket("TEST", TradeSide.SELL, 5, 120.0, "trim", "", ""));

        PortfolioView snapshot = sellResult.portfolio();

        assertEquals(9_600.0, snapshot.cash());
        assertEquals(10_200.0, snapshot.equity());
        assertEquals(200.0, snapshot.totalPnl());
        assertEquals(100.0, snapshot.realizedPnl());
        assertEquals(100.0, snapshot.unrealizedPnl());
        assertEquals(1, snapshot.positions().size());
        assertEquals(2, snapshot.recentTrades().size());
    }

    @Test
    void rejectsOversizedSell() {
        TradingLedger ledger = new TradingLedger(5_000.0);
        ledger.execute(new TradeTicket("ABC", TradeSide.BUY, 10, 50.0, "entry", "", ""));

        assertThrows(
            IllegalArgumentException.class,
            () -> ledger.execute(new TradeTicket("ABC", TradeSide.SELL, 50, 55.0, "oversell", "", ""))
        );
    }
}
