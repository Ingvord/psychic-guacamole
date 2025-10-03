package ru.ingvord.trading.telegram;

public class TelegramBotMessages {
    public static final String WELCOME = """
            🤖 **Welcome to Price Alert Bot!**
            
            I can help you track cryptocurrency prices and send you alerts when they reach your target ranges.
            
            **Quick Start:**
            • Use `/alert bitcoin 30000 50000` to set alerts
            • Use `/price bitcoin` to check current prices
            • Use `/list` to see your active alerts
            
            Type `/help` for all available commands.
            """;

    public static final String HELP = """
            📋 **Available Commands:**
            
            **Price Tracking:**
            • `/alert <symbol> <min> <max>` - Set price alert
              Example: `/alert bitcoin 30000 50000`
            
            • `/price <symbol>` - Get current price
              Example: `/price ethereum`
            
            **Alert Management:**
            • `/list` - Show all your active alerts
            • `/remove <alert_id>` - Remove specific alert
            • `/clear` - Remove all your alerts
            
            **Other:**
            • `/help` - Show this message
            • `/start` - Welcome message
            
            **Supported Symbols:** bitcoin, ethereum, cardano, solana, dogecoin, and many more!
            """;

    public static final String INVALID_ALERT = """
                ❌ **Invalid alert format!**
                
                **Correct usage:**
                `/alert <symbol> <min_price> <max_price>`
                
                **Example:**
                `/alert bitcoin 30000 50000`
                
                This will alert you when Bitcoin goes below $30,000 or above $50,000.
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
                    
                    Create one with: `/alert bitcoin 30000 50000`
                    """;

    public static final String INVALID_REMOVE = """
                ❌ **Invalid remove format!**
                
                **Correct usage:**
                `/remove <alert_id>`
                
                **Example:**
                `/remove a1b2c3d4`
                
                Use `/list` to see your alert IDs.
                """;

    public static final String INVALID_PRICE = """
                ❌ **Invalid price format!**
                
                **Correct usage:**
                `/price <symbol>`
                
                **Example:**
                `/price bitcoin`
                """;
}
