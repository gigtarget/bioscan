package com.example.bioscan.core.security

import com.example.bioscan.core.common.CryptoUtils
import com.example.bioscan.core.database.dao.AdminDao
import com.example.bioscan.core.database.entity.AdminEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AdminSecurityManager(private val adminDao: AdminDao) {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var lastActivityTimeMs: Long = 0L

    companion object {
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes timeout
    }

    suspend fun ensureDefaultAdminExists() {
        if (adminDao.getAdminCount() == 0) {
            val salt = CryptoUtils.generateSalt()
            val defaultPin = "123456" // Default initial PIN
            val hash = CryptoUtils.hashPassword(defaultPin, salt)

            val defaultAdmin = AdminEntity(
                adminId = UUID.randomUUID().toString(),
                username = "admin",
                passwordHash = hash,
                salt = salt,
                fullName = "System Administrator",
                role = "SUPER_ADMIN"
            )
            adminDao.insertAdmin(defaultAdmin)
        }
    }

    suspend fun authenticatePin(pin: String): Boolean {
        ensureDefaultAdminExists()
        val admin = adminDao.getAdminByUsername("admin") ?: return false
        val computedHash = CryptoUtils.hashPassword(pin, admin.salt)
        val success = computedHash == admin.passwordHash
        if (success) {
            _isLoggedIn.value = true
            updateActivityTime()
        }
        return success
    }

    suspend fun updatePin(oldPin: String, newPin: String): Boolean {
        if (!authenticatePin(oldPin)) return false
        val admin = adminDao.getAdminByUsername("admin") ?: return false
        val newSalt = CryptoUtils.generateSalt()
        val newHash = CryptoUtils.hashPassword(newPin, newSalt)

        val updatedAdmin = admin.copy(
            passwordHash = newHash,
            salt = newSalt
        )
        adminDao.insertAdmin(updatedAdmin)
        return true
    }

    fun updateActivityTime() {
        lastActivityTimeMs = System.currentTimeMillis()
    }

    fun checkSessionTimeout() {
        if (_isLoggedIn.value && (System.currentTimeMillis() - lastActivityTimeMs > SESSION_TIMEOUT_MS)) {
            _isLoggedIn.value = false
        }
    }

    fun logout() {
        _isLoggedIn.value = false
    }
}
