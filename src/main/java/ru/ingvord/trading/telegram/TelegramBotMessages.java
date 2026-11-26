package ru.ingvord.trading.telegram;

public class TelegramBotMessages {
    public static final String WELCOME = """
            🤖 **Welcome to Crypto Price Alert Bot!**
            
            Track cryptocurrency prices and get instant notifications when they reach your target levels.
            
            **Quick Start:**
            • `/alert BTC 40000 50000` - Get alerted when Bitcoin moves
            • `/price ETH` - Check current Ethereum price
            • `/list` - See all your alerts
            
            💡 Use ticker symbols (BTC, ETH, SOL) or full names (bitcoin, ethereum, solana)
            
            Type `/help` for all commands or `/tickers` for supported cryptocurrencies.
            """;

    public static final String HELP = """
            📋 **Available Commands:**
            
            **💰 Price Tracking:**
            • `/alert <ticker> <min> <max>` - Set price alert
              Example: `/alert BTC 40000 50000`
              Example: `/alert ETH 2000 3000`
            
            • `/price <ticker>` - Get current price
              Example: `/price BTC`
              Example: `/price ethereum`
            
            **📈 RSI Alerts:**
            • `/rsi <ticker> <tf> [period] [close|open|high|low]` - Notify when RSI enters overbought (70+) / oversold (30-)
              Timeframes: 15m 30m 1h 2h 4h 1d 1w. Default period 14, default source close
              Crypto only (Bybit spot data)
              Example: `/rsi BTC 4h`
            • `/rsi` - List your RSI alerts
            • `/rsi rm <id>` - Remove an RSI alert

            **📊 Alert Management:**
            • `/list` - Show all your active alerts
            • `/remove <alert_id>` - Remove specific alert
            • `/clear` - Remove all your alerts
            
            **ℹ️ Information:**
            • `/help` - Show this message
            • `/tickers` - Show supported cryptocurrencies
            
            **💡 Pro Tips:**
            • Use ticker symbols (BTC, ETH, SOL) for quick access
            • Or use full names (bitcoin, ethereum, solana)
            • You can have up to 20 active alerts
            • Alerts stay active until you remove them
            
            **⚠️ Important:**
            You'll receive continuous notifications while price is outside your range.
            This ensures you don't miss important price movements!
            """;

    public static final String INVALID_ALERT = """
            ❌ **Invalid alert format!**
            
            **Correct usage:**
            `/alert <ticker> <min_price> <max_price>`
            
            **Examples:**
            • `/alert BTC 40000 50000`
            • `/alert ETH 2000 3000`
            • `/alert SOL 50 150`
            
            This will alert you when the price goes **below** the minimum or **above** the maximum.
            
            💡 Tip: Use `/tickers` to see all supported cryptocurrencies
            """;

    public static final String ALERT_CREATED = """
                ✅ **Alert Created!**
                
                **Symbol:** %s
                **Current Price:** $%.2f
                **Alert Range:** $%.2f - $%.2f
                **Alert ID:** `%s`
                
                You'll be notified when %s goes outside this range.
                """;

    public static final String NO_ALERTS = """
            📭 **No Active Alerts**
            
            You don't have any active price alerts yet.
            
            **Create your first alert:**
            `/alert BTC 40000 50000`
            
            This will notify you when Bitcoin drops below $40,000 or rises above $50,000.
            
            💡 Check current prices with `/price <ticker>`
            """;

    public static final String INVALID_REMOVE = """
            ❌ **Invalid remove format!**
            
            **Correct usage:**
            `/remove <alert_id>`
            
            **Example:**
            `/remove a1b2c3d4`
            
            💡 Use `/list` to see your alert IDs
            """;

    public static final String INVALID_RSI = """
            ❌ **Invalid RSI command!**

            **Usage:**
            • `/rsi <ticker> <tf> [period] [close|open|high|low]`
            • `/rsi` - list your RSI alerts
            • `/rsi rm <id>` - remove an RSI alert

            **Timeframes:** 15m, 30m, 1h, 2h, 4h, 1d, 1w

            **Examples:**
            • `/rsi BTC 4h`
            • `/rsi ETH 1h 21 high`
            """;

    public static final String RSI_ALERT_CREATED = """
            ✅ **RSI Alert Created!**

            **Symbol:** %s
            **Timeframe:** %s (period %d, %s)
            **Current RSI:** %.2f
            **Alert ID:** `%s`

            You'll be notified when RSI enters overbought (70+) or oversold (30-).
            """;

    public static final String INVALID_PRICE = """
            ❌ **Invalid price format!**
            
            **Correct usage:**
            `/price <ticker>`
            
            **Examples:**
            • `/price BTC`
            • `/price ethereum`
            • `/price SOL`
            
            💡 Use `/tickers` to see all supported cryptocurrencies
            """;
}
