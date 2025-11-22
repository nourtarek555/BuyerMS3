package com.example.signallingms1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * NotificationHelper - Handles all notifications for the app
 * Supports order status changes, stock alerts, and promotional messages
 */
object NotificationHelper {
    
    private const val TAG = "NotificationHelper"
    
    // Notification channels
    private const val CHANNEL_ORDERS_ID = "order_notifications"
    private const val CHANNEL_ORDERS_NAME = "Order Updates"
    private const val CHANNEL_STOCK_ID = "stock_alerts"
    private const val CHANNEL_STOCK_NAME = "Stock Alerts"
    private const val CHANNEL_PROMOTIONS_ID = "promotions"
    private const val CHANNEL_PROMOTIONS_NAME = "Promotions"
    
    // Notification IDs
    private var orderNotificationId = 1000
    private var stockNotificationId = 2000
    private var promotionNotificationId = 3000
    
    // Track notifications sent to avoid duplicates (orderId + status combination)
    // Clear old entries periodically to prevent memory issues
    private val sentNotifications = mutableSetOf<String>()
    private const val MAX_TRACKED_NOTIFICATIONS = 100
    
    /**
     * Clear old notification tracking entries to prevent memory issues
     */
    private fun cleanupNotificationTracking() {
        if (sentNotifications.size > MAX_TRACKED_NOTIFICATIONS) {
            // Remove oldest entries (keep most recent 50)
            val entriesToRemove = sentNotifications.size - 50
            sentNotifications.removeAll(sentNotifications.take(entriesToRemove))
            Log.d(TAG, "Cleaned up notification tracking: removed $entriesToRemove old entries")
        }
    }
    
    /**
     * Initialize notification channels (required for Android 8.0+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Order notifications channel
            val orderChannel = NotificationChannel(
                CHANNEL_ORDERS_ID,
                CHANNEL_ORDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for order status updates"
                enableVibration(true)
                enableLights(true)
            }
            
            // Stock alerts channel
            val stockChannel = NotificationChannel(
                CHANNEL_STOCK_ID,
                CHANNEL_STOCK_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for stock alerts"
                enableVibration(true)
            }
            
            // Promotions channel
            val promotionChannel = NotificationChannel(
                CHANNEL_PROMOTIONS_ID,
                CHANNEL_PROMOTIONS_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Promotional messages and offers"
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(orderChannel)
            notificationManager.createNotificationChannel(stockChannel)
            notificationManager.createNotificationChannel(promotionChannel)
            
            Log.d(TAG, "Notification channels created")
        }
    }
    
    /**
     * Send notification for order status change
     * Only sends notifications for "ready" (pickup) and "delivering" (delivery) statuses
     * Checks delivery type to send appropriate notification message
     */
    fun notifyOrderStatusChange(
        context: Context,
        order: Order,
        previousStatus: String? = null
    ) {
        val currentStatus = order.status.lowercase()
        val deliveryType = order.deliveryType.lowercase()
        
        // Only send notifications for "ready" (ready for pickup) or "delivering" (out for delivery)
        if (currentStatus != "ready" && currentStatus != "delivering") {
            return
        }
        
        // Determine notification type based on status and delivery type
        val notificationType = when {
            currentStatus == "ready" && deliveryType == "delivery" -> "out_for_delivery"
            currentStatus == "ready" && deliveryType == "pickup" -> "ready_for_pickup"
            currentStatus == "delivering" && deliveryType == "delivery" -> "out_for_delivery"
            currentStatus == "delivering" && deliveryType == "pickup" -> "ready_for_pickup"
            else -> return // Should not reach here
        }
        
        // Check if we've already sent a notification for this order + notification type combination
        val notificationKey = "${order.orderId}_$notificationType"
        if (sentNotifications.contains(notificationKey)) {
            Log.d(TAG, "Notification already sent for order ${order.orderId} with type $notificationType - skipping")
            return
        }
        
        // Only send if status actually changed
        if (previousStatus != null && previousStatus.lowercase() == currentStatus) {
            return
        }
        
        // Determine notification title and message based on notification type
        val (title, message) = when (notificationType) {
            "ready_for_pickup" -> "Ready for Pickup!" to "Your order #${order.orderId.take(8)} is ready for pickup from ${order.sellerName}"
            "out_for_delivery" -> "Out for Delivery!" to "Your order #${order.orderId.take(8)} is out for delivery. Expected soon!"
            else -> return // Should not reach here
        }
        
        Log.d(TAG, "Sending notification: $title for order ${order.orderId} (status: $currentStatus, deliveryType: $deliveryType)")
        
        // Mark notification as sent
        sentNotifications.add(notificationKey)
        cleanupNotificationTracking() // Clean up old entries if needed
        
        // Create intent to open order details
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to_order", order.orderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            orderNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ORDERS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        
        try {
            with(NotificationManagerCompat.from(context)) {
                if (areNotificationsEnabled()) {
                    notify(orderNotificationId++, notification)
                    Log.d(TAG, "Order notification sent: $title")
                } else {
                    Log.w(TAG, "Notifications are disabled by user")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission denied: ${e.message}")
        }
    }
    
    /**
     * Send notification for stock alerts
     */
    fun notifyStockAlert(
        context: Context,
        productName: String,
        currentStock: Int
    ) {
        val title = "Low Stock Alert"
        val message = if (currentStock == 0) {
            "$productName is out of stock!"
        } else {
            "$productName has low stock ($currentStock remaining)"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_STOCK_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        try {
            with(NotificationManagerCompat.from(context)) {
                if (areNotificationsEnabled()) {
                    notify(stockNotificationId++, notification)
                    Log.d(TAG, "Stock alert notification sent: $title")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission denied: ${e.message}")
        }
    }
    
    /**
     * Send promotional notification
     */
    fun notifyPromotion(
        context: Context,
        title: String,
        message: String
    ) {
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            promotionNotificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_PROMOTIONS_ID)
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        try {
            with(NotificationManagerCompat.from(context)) {
                if (areNotificationsEnabled()) {
                    notify(promotionNotificationId++, notification)
                    Log.d(TAG, "Promotion notification sent: $title")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission denied: ${e.message}")
        }
    }
}

