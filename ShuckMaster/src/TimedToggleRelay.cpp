#include "TimedToggleRelay.h"

void TimedToggleRelay::Begin() {
  _on = false;
  pinMode(_pin, OUTPUT);
  digitalWrite(_pin, _on);
  _lastToggle = millis();  
}

void TimedToggleRelay::Tick() {
  if (millis() - _lastToggle >= _period) {
    _on = !_on;
    digitalWrite(_pin, _on);
    _lastToggle = millis();
  }
}