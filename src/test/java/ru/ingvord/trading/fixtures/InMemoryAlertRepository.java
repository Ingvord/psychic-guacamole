package ru.ingvord.trading.fixtures;

import ru.ingvord.trading.model.PriceAlert;
import ru.ingvord.trading.model.RsiAlert;
import ru.ingvord.trading.repository.AlertRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Map-backed AlertRepository mirroring the status semantics of the DynamoDB queries. */
public class InMemoryAlertRepository extends AlertRepository {
    public final Map<String, PriceAlert> priceAlerts = new LinkedHashMap<>();
    public final Map<String, RsiAlert> rsiAlerts = new LinkedHashMap<>();

    public InMemoryAlertRepository() {
        super("in-memory", null);
    }

    @Override
    public void save(PriceAlert alert) {
        priceAlerts.put(alert.alertId(), alert);
    }

    @Override
    public void updateStatus(String alertId, String status) {
        PriceAlert a = priceAlerts.get(alertId);
        priceAlerts.put(alertId, new PriceAlert(a.alertId(), a.chatId(), a.symbol(),
                a.targetPriceMin(), a.targetPriceMax(), a.createdAt(), status));
    }

    @Override
    public List<PriceAlert> findAll() {
        return List.copyOf(priceAlerts.values());
    }

    /** Like ChatIdIndex query: status is ACTIVE or absent. */
    @Override
    public List<PriceAlert> findByChatId(long chatId) {
        return priceAlerts.values().stream()
                .filter(a -> a.chatId() == chatId)
                .filter(a -> a.status() == null || ALERT_STATUS_ACTIVE.equalsIgnoreCase(a.status()))
                .toList();
    }

    @Override
    public List<PriceAlert> findByStatus(String status) {
        return priceAlerts.values().stream()
                .filter(a -> status.equalsIgnoreCase(a.status()))
                .toList();
    }

    @Override
    public void delete(String alertId) {
        priceAlerts.remove(alertId);
        rsiAlerts.remove(alertId);
    }

    @Override
    public void saveRsi(RsiAlert alert) {
        rsiAlerts.put(alert.alertId(), alert);
    }

    @Override
    public void updateRsiZone(String alertId, String zone) {
        RsiAlert a = Objects.requireNonNull(rsiAlerts.get(alertId), alertId);
        rsiAlerts.put(alertId, new RsiAlert(a.alertId(), a.chatId(), a.symbol(), a.interval(),
                a.period(), a.seriesType(), zone, a.createdAt(), a.status()));
    }

    @Override
    public List<RsiAlert> findRsiByStatus() {
        return rsiAlerts.values().stream()
                .filter(a -> ALERT_STATUS_RSI_ACTIVE.equals(a.status()))
                .toList();
    }

    @Override
    public List<RsiAlert> findRsiByChatId(long chatId) {
        return findRsiByStatus().stream().filter(a -> a.chatId() == chatId).toList();
    }
}
