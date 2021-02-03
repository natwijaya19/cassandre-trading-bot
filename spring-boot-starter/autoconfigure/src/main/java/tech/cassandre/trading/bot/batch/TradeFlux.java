package tech.cassandre.trading.bot.batch;

import tech.cassandre.trading.bot.domain.Trade;
import tech.cassandre.trading.bot.dto.trade.TradeDTO;
import tech.cassandre.trading.bot.repository.OrderRepository;
import tech.cassandre.trading.bot.repository.TradeRepository;
import tech.cassandre.trading.bot.service.TradeService;
import tech.cassandre.trading.bot.util.base.batch.BaseExternalFlux;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Trade flux - push {@link TradeDTO}.
 */
public class TradeFlux extends BaseExternalFlux<TradeDTO> {

    /** Trade service. */
    private final TradeService tradeService;

    /** Order repository. */
    private final OrderRepository orderRepository;

    /** Trade repository. */
    private final TradeRepository tradeRepository;

    /**
     * Constructor.
     *
     * @param newTradeService    trade service
     * @param newOrderRepository order repository
     * @param newTradeRepository trade repository
     */
    public TradeFlux(final TradeService newTradeService,
                     final OrderRepository newOrderRepository,
                     final TradeRepository newTradeRepository) {
        this.tradeRepository = newTradeRepository;
        this.orderRepository = newOrderRepository;
        this.tradeService = newTradeService;
    }

    @Override
    protected final Set<TradeDTO> getNewValues() {
        logger.debug("TradeFlux - Retrieving new values");
        Set<TradeDTO> newValues = new LinkedHashSet<>();

        // Finding which trades has been updated.
        tradeService.getTrades().forEach(trade -> {
            logger.debug("TradeFlux - Treating trade : {}", trade.getTradeId());
            final Optional<Trade> tradeInDatabase = tradeRepository.findByTradeId(trade.getTradeId());
            if (tradeInDatabase.isEmpty() || !tradeMapper.mapToTradeDTO(tradeInDatabase.get()).equals(trade)) {
                logger.debug("TradeFlux - Trade {} has changed : {}", trade.getTradeId(), trade);
                newValues.add(trade);
            }
        });
        logger.debug("TradeFlux - {} trade(s) updated", newValues.size());
        return newValues;
    }

    @Override
    public final Optional<TradeDTO> saveValue(final TradeDTO newValue) {
        AtomicReference<Trade> valueToSave = new AtomicReference<>();

        tradeRepository.findByTradeId(newValue.getTradeId())
                .ifPresentOrElse(trade -> {
                    // Update trade.
                    tradeMapper.updateOrder(newValue, trade);
                    valueToSave.set(trade);
                    logger.debug("TradeFlux - Updating trade in database {}", trade);
                }, () -> {
                    // Create trade.
                    final Trade newTrade = tradeMapper.mapToTrade(newValue);
                    orderRepository.findByOrderId(newValue.getOrderId())
                            .ifPresent(value -> newTrade.setOrder(value.getId()));
                    valueToSave.set(newTrade);
                    logger.debug("TradeFlux - Creating trade in database {}", newTrade);
                });

        return Optional.ofNullable(tradeMapper.mapToTradeDTO(tradeRepository.save(valueToSave.get())));
    }

}
