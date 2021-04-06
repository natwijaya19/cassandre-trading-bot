package tech.cassandre.trading.bot.test.strategy.multiple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tech.cassandre.trading.bot.batch.AccountFlux;
import tech.cassandre.trading.bot.domain.Strategy;
import tech.cassandre.trading.bot.dto.user.AccountDTO;
import tech.cassandre.trading.bot.dto.util.CurrencyPairDTO;
import tech.cassandre.trading.bot.repository.StrategyRepository;
import tech.cassandre.trading.bot.service.ExchangeService;
import tech.cassandre.trading.bot.test.util.junit.BaseTest;
import tech.cassandre.trading.bot.test.util.junit.configuration.Configuration;
import tech.cassandre.trading.bot.test.util.junit.configuration.Property;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;
import static tech.cassandre.trading.bot.dto.util.CurrencyDTO.BTC;
import static tech.cassandre.trading.bot.dto.util.CurrencyDTO.ETH;
import static tech.cassandre.trading.bot.dto.util.CurrencyDTO.USDT;
import static tech.cassandre.trading.bot.test.strategy.basic.TestableCassandreStrategy.PARAMETER_TESTABLE_STRATEGY_ENABLED;
import static tech.cassandre.trading.bot.test.strategy.multiple.Strategy1.PARAMETER_STRATEGY_1_ENABLED;
import static tech.cassandre.trading.bot.test.strategy.multiple.Strategy2.PARAMETER_STRATEGY_2_ENABLED;
import static tech.cassandre.trading.bot.test.strategy.multiple.Strategy3.PARAMETER_STRATEGY_3_ENABLED;
import static tech.cassandre.trading.bot.test.strategy.ta4j.TestableTa4jCassandreStrategy.PARAMETER_TESTABLE_TA4J_STRATEGY_ENABLED;
import static tech.cassandre.trading.bot.test.util.junit.configuration.ConfigurationExtension.PARAMETER_EXCHANGE_DRY;
import static tech.cassandre.trading.bot.test.util.strategies.InvalidStrategy.PARAMETER_INVALID_STRATEGY_ENABLED;
import static tech.cassandre.trading.bot.test.util.strategies.NoTradingAccountStrategy.PARAMETER_NO_TRADING_ACCOUNT_STRATEGY_ENABLED;

@SpringBootTest
@DisplayName("Strategy - Running multiple strategies")
@Configuration({
        @Property(key = PARAMETER_INVALID_STRATEGY_ENABLED, value = "false"),
        @Property(key = PARAMETER_TESTABLE_STRATEGY_ENABLED, value = "false"),
        @Property(key = PARAMETER_TESTABLE_TA4J_STRATEGY_ENABLED, value = "false"),
        @Property(key = PARAMETER_NO_TRADING_ACCOUNT_STRATEGY_ENABLED, value = "false"),
        // Using strategies 1, 2 & 3 in dry mode.
        @Property(key = PARAMETER_STRATEGY_1_ENABLED, value = "true"),
        @Property(key = PARAMETER_STRATEGY_2_ENABLED, value = "true"),
        @Property(key = PARAMETER_STRATEGY_3_ENABLED, value = "true"),
        @Property(key = PARAMETER_EXCHANGE_DRY, value = "true")
})
@ActiveProfiles("schedule-disabled")
@DirtiesContext(classMode = AFTER_CLASS)
public class MultipleStrategiesTest extends BaseTest {

    @Autowired
    private AccountFlux accountFlux;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private Strategy1 strategy1;

    @Autowired
    private Strategy2 strategy2;

    @Autowired
    private Strategy3 strategy3;

    @Test
    //@CaseId(82) TODO Create the test case in Qase
    @DisplayName("Check multiple strategies behavior")
    public void checkMultipleStrategyBehavior() {
        //==============================================================================================================
        // Strategies tested.
        // Strategy 1 - Requesting BTC/USDT.
        // Strategy 2 - Requesting BTC/ETH.
        // Strategy 3 - Requesting BTC/USDT & ETH/USDT.

        //==============================================================================================================
        // Checking the MarketService have all the currency pairs.
        final Set<CurrencyPairDTO> availableCurrencyPairs = exchangeService.getAvailableCurrencyPairs();
        assertEquals(3, availableCurrencyPairs.size());
        assertTrue(availableCurrencyPairs.contains(BTC_USDT));
        assertTrue(availableCurrencyPairs.contains(BTC_ETH));
        assertTrue(availableCurrencyPairs.contains(ETH_USDT));

        //==============================================================================================================
        // Checking the three strategies are stored in database.
        assertEquals(3, strategyRepository.count());
        final Optional<Strategy> s1 = strategyRepository.findByStrategyId("01");
        assertTrue(s1.isPresent());
        assertEquals(1, s1.get().getId());
        assertEquals("01", s1.get().getStrategyId());
        assertEquals("Strategy 1", s1.get().getName());
        final Optional<Strategy> s2 = strategyRepository.findByStrategyId("02");
        assertTrue(s2.isPresent());
        assertEquals(2, s2.get().getId());
        assertEquals("02", s2.get().getStrategyId());
        assertEquals("Strategy 2", s2.get().getName());
        final Optional<Strategy> s3 = strategyRepository.findByStrategyId("03");
        assertTrue(s3.isPresent());
        assertEquals(3, s3.get().getId());
        assertEquals("03", s3.get().getStrategyId());
        assertEquals("Strategy 3", s3.get().getName());

        //==============================================================================================================
        // Check balances on each strategy & onAccountUpdate().
        accountFlux.update();
        await().untilAsserted(() -> assertEquals(3, strategy3.getAccountsUpdatesReceived().size()));

        // Strategy 1 test.
        Map<String, AccountDTO> strategyAccounts = strategy1.getAccounts();
        Optional<AccountDTO> strategyTradeAccount = strategy1.getTradeAccount();
        assertEquals(3, strategyAccounts.size());
        assertTrue(strategyAccounts.containsKey("main"));
        assertTrue(strategyAccounts.containsKey("trade"));
        assertTrue(strategyAccounts.containsKey("savings"));
        assertTrue(strategyTradeAccount.isPresent());
        assertEquals("trade", strategyTradeAccount.get().getName());
        assertEquals(3, strategyTradeAccount.get().getBalances().size());
        assertTrue(strategyTradeAccount.get().getBalance(BTC).isPresent());
        assertEquals(0, new BigDecimal("0.99962937").compareTo(strategyTradeAccount.get().getBalance(BTC).get().getAvailable()));
        assertTrue(strategyTradeAccount.get().getBalance(USDT).isPresent());
        assertEquals(0, new BigDecimal("1000").compareTo(strategyTradeAccount.get().getBalance(USDT).get().getAvailable()));
        assertTrue(strategyTradeAccount.get().getBalance(ETH).isPresent());
        assertEquals(0, new BigDecimal("10").compareTo(strategyTradeAccount.get().getBalance(ETH).get().getAvailable()));

        // Strategy 2 test.
        strategyAccounts = strategy2.getAccounts();
        strategyTradeAccount = strategy2.getTradeAccount();
        assertEquals(3, strategyAccounts.size());
        assertTrue(strategyAccounts.containsKey("main"));
        assertTrue(strategyAccounts.containsKey("trade"));
        assertTrue(strategyAccounts.containsKey("savings"));
        assertTrue(strategyTradeAccount.isPresent());
        assertEquals("trade", strategyTradeAccount.get().getName());
        assertEquals(3, strategyTradeAccount.get().getBalances().size());
        assertTrue(strategyTradeAccount.get().getBalance(BTC).isPresent());
        assertEquals(0, new BigDecimal("0.99962937").compareTo(strategyTradeAccount.get().getBalance(BTC).get().getAvailable()));
        assertTrue(strategyTradeAccount.get().getBalance(USDT).isPresent());
        assertEquals(0, new BigDecimal("1000").compareTo(strategyTradeAccount.get().getBalance(USDT).get().getAvailable()));
        assertTrue(strategyTradeAccount.get().getBalance(ETH).isPresent());
        assertEquals(0, new BigDecimal("10").compareTo(strategyTradeAccount.get().getBalance(ETH).get().getAvailable()));

        // Strategy 3 test.
        strategyAccounts = strategy3.getAccounts();
        strategyTradeAccount = strategy3.getTradeAccount();
        assertEquals(3, strategyAccounts.size());
        assertTrue(strategyAccounts.containsKey("main"));
        assertTrue(strategyAccounts.containsKey("trade"));
        assertTrue(strategyAccounts.containsKey("savings"));
        assertTrue(strategyTradeAccount.isPresent());
        assertEquals("trade", strategyTradeAccount.get().getName());
        assertEquals(3, strategyTradeAccount.get().getBalances().size());
        assertTrue(strategyTradeAccount.get().getBalance(BTC).isPresent());
        assertEquals(0, new BigDecimal("0.99962937").compareTo(strategyTradeAccount.get().getBalance(BTC).get().getAvailable()));
        assertTrue(strategyTradeAccount.get().getBalance(USDT).isPresent());
        assertEquals(0, new BigDecimal("1000").compareTo(strategyTradeAccount.get().getBalance(USDT).get().getAvailable()));
        assertTrue(strategyTradeAccount.get().getBalance(ETH).isPresent());
        assertEquals(0, new BigDecimal("10").compareTo(strategyTradeAccount.get().getBalance(ETH).get().getAvailable()));

        //==============================================================================================================
        // Checking received tickers by each tickers & getLastTickers().

        //==============================================================================================================
        // Strategy 1 - Creating 2 positions and see if they are opened.
        // Check positionId.
        // Check onPositionUpdate() & onPositionStatusUpdate().
        // Check onOrderUpdate().
        // Check onTradeUpdate().
        // Check getOrders() & getOrderByOrderId().
        // Check getTrades() & getTradeByTradeId().
        // Check getAmountsLockedByPosition().

        //==============================================================================================================
        // Strategy 2 - Creating 1 position and see if it's opened.
        // Check positionId.
        // Check onPositionUpdate() & onPositionStatusUpdate().
        // Check onOrderUpdate().
        // Check onTradeUpdate().
        // Check getAmountsLockedByPosition().

        //==============================================================================================================
        // Strategy 3 - Creating 3 positions and see if they are opened.
        // Check positionId.
        // Check onPositionUpdate() & onPositionStatusUpdate().
        // Check onOrderUpdate().
        // Check onTradeUpdate().
        // Check getAmountsLockedByPosition().

        //==============================================================================================================
        // Check balances, canBuy() & canSell().

        //==============================================================================================================
        // New tickers - Check latestCalculatedGain on all positions.

        //==============================================================================================================
        // Strategy 1 - close 1 position.
        // Strategy 2 - close 2 positions.
        // Strategy 3 - close 2 positions.
        // Check internal methods.
        // Check getPositions() & getPositionByPositionId().
        // Check getGains().
        // Check getAmountsLockedByPosition().

        //==============================================================================================================
        // Check balances, canBuy() & canSell().

    }

}
