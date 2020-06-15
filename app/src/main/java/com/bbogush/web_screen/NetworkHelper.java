package com.bbogush.web_screen;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;
import java.util.LinkedList;
import java.util.List;

public class NetworkHelper {
    private static final String TAG = NetworkHelper.class.getSimpleName();

    private ConnectivityManager connectivityManager;
    private OnNetworkChangeListener onNetworkChangeListener;

    public class IpInfo {
        public String interfaceName;
        public String interfaceType;
        public List<LinkAddress> addresses;
    }

    public interface OnNetworkChangeListener {
        void onChange();
    }

    public NetworkHelper(Context context, OnNetworkChangeListener callback) {
        onNetworkChangeListener = callback;

        connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    public void close() {
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {

                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "Network available");
                    super.onAvailable(network);
                    onNetworkChangeListener.onChange();
                }

                @Override
                public void onLost(Network network) {
                    Log.d(TAG, "Network lost");
                    super.onLost(network);
                    onNetworkChangeListener.onChange();
                }
            };

    private String getInterfaceType(NetworkCapabilities networkCapabilities) {
        String interfaceType;

        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            interfaceType = "Mobile";
        else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            interfaceType = "Wi-Fi";
        else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
            interfaceType = "Bluetooth";
        else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            interfaceType = "Ethernet";
        else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            interfaceType = "VPN";
        else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            interfaceType = "Wi-Fi Aware";
        else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN))
            interfaceType = "LoWPAN";
        else
            interfaceType = "Unknown";

        return interfaceType;
    }

    public List<IpInfo> getIpInfo() {
        List<IpInfo> ipInfoList = new LinkedList<>();

        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            NetworkCapabilities networkCapabilities =
                    connectivityManager.getNetworkCapabilities(network);

            if (linkProperties == null || networkCapabilities == null) {
                Log.e(TAG, "Failed to get network properties");
                continue;
            }

            String interfaceName = linkProperties.getInterfaceName();
            if (interfaceName == null) {
                Log.e(TAG, "Failed to get interface name");
                continue;
            }

            IpInfo ipInfo = new IpInfo();
            ipInfo.interfaceName = interfaceName;
            ipInfo.interfaceType = getInterfaceType(networkCapabilities);
            ipInfo.addresses = new LinkedList<>();
            List<LinkAddress> addresses = linkProperties.getLinkAddresses();
            for (LinkAddress address : addresses) {
                if (address.getAddress().isLinkLocalAddress())
                    continue;
                ipInfo.addresses.add(address);
            }

            ipInfoList.add(ipInfo);
        }

        return ipInfoList;
    }
}
