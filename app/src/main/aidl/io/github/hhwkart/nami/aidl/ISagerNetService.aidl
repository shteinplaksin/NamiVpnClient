package io.github.hhwkart.nami.aidl;

import io.github.hhwkart.nami.aidl.ISagerNetServiceCallback;

interface ISagerNetService {
  int getState();
  String getProfileName();

  void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);

  int urlTest();

  /**
   * Credentials of the local authenticated inbound for this app's own
   * internal HTTP clients. Empty strings when the service is not running.
   */
  String getProxyAuthUser();
  String getProxyAuthPass();
}
