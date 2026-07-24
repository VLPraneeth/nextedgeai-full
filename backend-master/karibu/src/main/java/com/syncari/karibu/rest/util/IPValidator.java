package com.syncari.karibu.rest.util;

import com.syncari.karibu.rest.exceptions.UnauthorizedException;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IPValidator {

    public boolean isValidIPInCIDRRange(String ipAddress, String cidrRange) {
        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            InetAddress[] cidrAddresses = InetAddress.getAllByName(cidrRange);

            // Extract network address and mask from the CIDR address
            InetAddress networkAddress = cidrAddresses[0];
            int prefixLength = getPrefixLengthFromCIDR(cidrRange);

            // Create a mask based on the prefix length
            byte[] maskBytes = new byte[4];
            for (int i = 0; i < prefixLength / 8; i++) {
                maskBytes[i] = (byte) 0xFF;
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits > 0) {
                maskBytes[prefixLength / 8] = (byte) ((1 << (8 - remainingBits)) - 1);
            }

            // Apply the mask to both the network address and the IP address to be checked
            byte[] networkAddressBytes = networkAddress.getAddress();
            byte[] ipAddressBytes = ip.getAddress();
            for (int i = 0; i < 4; i++) {
                networkAddressBytes[i] &= maskBytes[i];
                ipAddressBytes[i] &= maskBytes[i];
            }

            // Compare the masked IP addresses
            return Arrays.equals(networkAddressBytes, ipAddressBytes);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static int getPrefixLengthFromCIDR(String cidrRange) {
        int index = cidrRange.indexOf('/');
        if (index == -1) {
            throw new IllegalArgumentException("Invalid CIDR range format");
        }
        return Integer.parseInt(cidrRange.substring(index + 1));
    }

    public boolean isClientIpPermitted(final String clientIp, List<String> listIps){
        boolean result = true;
        if (CollectionUtils.isNotEmpty(listIps) && !listIps.contains(clientIp)){
            result = false;
        }
        for (String ip : listIps){
            if (ip.contains("/")){
                if (isValidIPInCIDRRange(clientIp, ip)){
                    return true;
                }
            }
        }
        return result;
    }
}
