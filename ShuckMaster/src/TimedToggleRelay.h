#ifndef TIMED_TOGGLE_RELAY_H_
#define TIMED_TOGGLE_RELAY_H_

#include <Arduino.h>

class TimedToggleRelay {
 public:
  TimedToggleRelay(uint8_t pin, unsigned long period): _pin(pin), _period(period), _lastToggle(0), _on(false) { }
  
  void Begin();
  
  void Tick();
    
 private:
  unsigned long _period;
  unsigned long _lastToggle;
  uint8_t _pin;
  bool _on;
};

#endif