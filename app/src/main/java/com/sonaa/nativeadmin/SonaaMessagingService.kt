package com.sonaa.nativeadmin
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SonaaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("sonaa_admin_session", Context.MODE_PRIVATE).edit().putString("pending_fcm_token", token).apply()
        sendBroadcast(Intent(ACTION_TOKEN_REFRESHED).setPackage(packageName).putExtra("token", token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"].orEmpty()
        val channel = channelFor(type)
        val title = message.notification?.title ?: message.data["title"] ?: "إشعار من صُنّاع"
        val body = message.notification?.body ?: message.data["message"] ?: ""
        val allowedDestinations = setOf("orders", "storeOrders", "commerceSubscriptions", "technicians", "clients", "clientWallet", "wallet", "recovery", "support", "topups", "wallets")
        val destination = message.data["destination"]?.takeIf { it in allowedDestinations } ?: when (channel) {
            CHANNEL_ORDERS, CHANNEL_STORE_ORDERS -> "orders"
            CHANNEL_TECHNICIAN_REGISTRATION, CHANNEL_TECHNICIAN_ACTIVITY, CHANNEL_TECHNICIAN_PROFILE -> "technicians"
            CHANNEL_CLIENT_REGISTRATION, CHANNEL_CLIENT_ACTIVITY -> "clients"
            CHANNEL_SUBSCRIPTIONS -> "commerceSubscriptions"
            CHANNEL_PASSWORD_RECOVERY -> "recovery"
            CHANNEL_SUPPORT_COMPLAINTS -> "support"
            CHANNEL_WALLET_TOPUPS -> "topups"
            CHANNEL_SERVICE_PAYMENTS -> "wallet"
            else -> "dashboard"
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("destination", destination)
            putExtra("link", message.data["link"].orEmpty())
        }
        val pending = PendingIntent.getActivity(this, type.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val soundUri = Uri.parse("android.resource://$packageName/${R.raw.hamzawysat_admin_notification}")
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pending)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        val notificationId = message.data["notificationId"]?.takeIf { it.isNotBlank() }?.hashCode() ?: type.hashCode()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    private fun channelFor(type: String) = when (type) {
        "order_new", "new_order", "admin_new_order", "admin_order_offer_accepted",
        "admin_order_cancelled", "admin_order_completed", "admin_fanni_offer",
        "admin_price_change_requested", "admin_client_rating" -> CHANNEL_ORDERS

        "admin_store_order" -> CHANNEL_STORE_ORDERS

        "fanni_register", "new_fanni", "admin_new_technician" -> CHANNEL_TECHNICIAN_REGISTRATION
        "admin_fanni_login", "admin_fanni_logout" -> CHANNEL_TECHNICIAN_ACTIVITY
        "admin_fanni_profile_edit" -> CHANNEL_TECHNICIAN_PROFILE

        "client_register", "new_client", "admin_new_client" -> CHANNEL_CLIENT_REGISTRATION
        "admin_client_login", "admin_client_logout" -> CHANNEL_CLIENT_ACTIVITY

        "admin_subscription_request", "admin_subscription_renewed" -> CHANNEL_SUBSCRIPTIONS
        "password_reset", "admin_password_recovery", "admin_password_recovery_message" -> CHANNEL_PASSWORD_RECOVERY
        "support", "new_complaint", "admin_support_request", "admin_new_complaint",
        "admin_support_followup" -> CHANNEL_SUPPORT_COMPLAINTS

        "wallet_charge", "topup_request", "admin_new_topup", "admin_client_topup",
        "wallet_credit", "fanni_wallet_settlement", "technician_settlement", "topup_approved",
        "topup_rejected", "wallet_debit", "commission_deducted", "client_wallet_topup_approved",
        "client_wallet_topup_rejected", "client_wallet_credit", "client_wallet_debit",
        "client_wallet_settlement", "client_service_refunded" -> CHANNEL_WALLET_TOPUPS

        "admin_service_payment_held" -> CHANNEL_SERVICE_PAYMENTS
        else -> CHANNEL_SUPPORT_COMPLAINTS
    }

    companion object {
        const val ACTION_TOKEN_REFRESHED = "com.sonaa.nativeadmin.FCM_TOKEN_REFRESHED"

        private const val CHANNEL_ORDERS = "admin_orders_v3"
        private const val CHANNEL_STORE_ORDERS = "admin_store_orders_v3"
        private const val CHANNEL_CLIENT_REGISTRATION = "admin_client_registration_v3"
        private const val CHANNEL_CLIENT_ACTIVITY = "admin_client_activity_v3"
        private const val CHANNEL_TECHNICIAN_REGISTRATION = "admin_technician_registration_v3"
        private const val CHANNEL_TECHNICIAN_ACTIVITY = "admin_technician_activity_v3"
        private const val CHANNEL_TECHNICIAN_PROFILE = "admin_technician_profile_v3"
        private const val CHANNEL_SUBSCRIPTIONS = "admin_subscriptions_v3"
        private const val CHANNEL_PASSWORD_RECOVERY = "admin_password_recovery_v3"
        private const val CHANNEL_SUPPORT_COMPLAINTS = "admin_support_complaints_v3"
        private const val CHANNEL_WALLET_TOPUPS = "admin_wallet_topups_v3"
        private const val CHANNEL_SERVICE_PAYMENTS = "admin_service_payments_v3"

        fun createChannels(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= 26) {
                // Remove the six legacy grouped channels so Android shows the
                // twelve current notification categories instead of both sets.
                listOf(
                    "order_new_v2", "fanni_register_v2", "client_register_v2",
                    "password_reset_v2", "support_v2", "wallet_charge_v2"
                ).forEach(manager::deleteNotificationChannel)

                val soundUri = Uri.parse("android.resource://com.sonaa.nativeadmin/${R.raw.hamzawysat_admin_notification}")
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                listOf(
                    CHANNEL_CLIENT_REGISTRATION to "تسجيل عميل جديد",
                    CHANNEL_CLIENT_ACTIVITY to "نشاط العملاء",
                    CHANNEL_TECHNICIAN_REGISTRATION to "تسجيل فني جديد",
                    CHANNEL_TECHNICIAN_ACTIVITY to "نشاط الفنيين",
                    CHANNEL_TECHNICIAN_PROFILE to "تعديلات ملفات الفنيين",
                    CHANNEL_ORDERS to "الطلبات",
                    CHANNEL_STORE_ORDERS to "طلبات المتجر",
                    CHANNEL_SUBSCRIPTIONS to "الاشتراكات",
                    CHANNEL_PASSWORD_RECOVERY to "استعادة كلمة المرور",
                    CHANNEL_SUPPORT_COMPLAINTS to "الدعم والشكاوى",
                    CHANNEL_WALLET_TOPUPS to "شحن المحافظ",
                    CHANNEL_SERVICE_PAYMENTS to "مدفوعات الخدمات"
                ).forEach { (id, name) ->
                    val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
                    channel.setSound(soundUri, audioAttributes)
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }
}
