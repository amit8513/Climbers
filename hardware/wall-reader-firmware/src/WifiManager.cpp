#include "WifiManager.h"

WifiManager::WifiManager(const char* ssid, const char* password, uint32_t connectTimeoutMs, uint32_t retryIntervalMs)
    : ssid_(ssid), password_(password), connectTimeoutMs_(connectTimeoutMs), retryIntervalMs_(retryIntervalMs) {}

void WifiManager::begin() {
  WiFi.mode(WIFI_STA);
  startConnectAttempt();
}

void WifiManager::startConnectAttempt() {
  Serial.printf("[wifi] connecting to '%s'\n", ssid_);
  WiFi.begin(ssid_, password_);
  connectAttemptStartMs_ = millis();
  connecting_ = true;
}

void WifiManager::update() {
  if (WiFi.status() == WL_CONNECTED) {
    if (connecting_) {
      Serial.printf("[wifi] connected, ip=%s\n", WiFi.localIP().toString().c_str());
    }
    connecting_ = false;
    return;
  }

  if (connecting_) {
    if (millis() - connectAttemptStartMs_ > connectTimeoutMs_) {
      Serial.println("[wifi] connect attempt timed out, will retry");
      connecting_ = false;
      lastRetryAttemptMs_ = millis();
    }
    return; // still within the connect attempt window - non-blocking wait
  }

  if (millis() - lastRetryAttemptMs_ >= retryIntervalMs_) {
    startConnectAttempt();
  }
}

bool WifiManager::isConnected() {
  return WiFi.status() == WL_CONNECTED;
}
