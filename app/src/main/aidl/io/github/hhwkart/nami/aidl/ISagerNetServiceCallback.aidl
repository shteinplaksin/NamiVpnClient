package io.github.hhwkart.nami.aidl;

import io.github.hhwkart.nami.aidl.SpeedDisplayData;
import io.github.hhwkart.nami.aidl.TrafficData;

oneway interface ISagerNetServiceCallback {
  void stateChanged(int state, String profileName, String msg);
  void missingPlugin(String profileName, String pluginName);
  void cbSpeedUpdate(in SpeedDisplayData stats);
  void cbTrafficUpdate(in TrafficData stats);
  void cbSelectorUpdate(long id);
}
