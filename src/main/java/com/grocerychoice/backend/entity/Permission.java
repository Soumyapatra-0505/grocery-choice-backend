package com.grocerychoice.backend.entity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * System and operational permissions for Grocery Choice staff, admin, and owner roles.
 */
public final class Permission {

    public static final String VIEW_DASHBOARD = "VIEW_DASHBOARD";
    public static final String MANAGE_PRODUCTS = "MANAGE_PRODUCTS";
    public static final String MANAGE_CATEGORIES = "MANAGE_CATEGORIES";
    public static final String MANAGE_INVENTORY = "MANAGE_INVENTORY";
    public static final String MANAGE_ORDERS = "MANAGE_ORDERS";
    public static final String MANAGE_CUSTOMERS = "MANAGE_CUSTOMERS";
    public static final String MANAGE_DELIVERY = "MANAGE_DELIVERY";
    public static final String VIEW_REPORTS = "VIEW_REPORTS";
    public static final String MANAGE_STAFF = "MANAGE_STAFF";
    public static final String MANAGE_ADMINS = "MANAGE_ADMINS";
    public static final String MANAGE_OWNERS = "MANAGE_OWNERS";
    public static final String MANAGE_DESIGNATIONS = "MANAGE_DESIGNATIONS";
    public static final String MANAGE_PERMISSIONS = "MANAGE_PERMISSIONS";
    public static final String TRANSFER_OWNERSHIP = "TRANSFER_OWNERSHIP";

    private Permission() {
    }

    public static Set<String> allPermissions() {
        Set<String> set = new LinkedHashSet<>();
        set.add(VIEW_DASHBOARD);
        set.add(MANAGE_PRODUCTS);
        set.add(MANAGE_CATEGORIES);
        set.add(MANAGE_INVENTORY);
        set.add(MANAGE_ORDERS);
        set.add(MANAGE_CUSTOMERS);
        set.add(MANAGE_DELIVERY);
        set.add(VIEW_REPORTS);
        set.add(MANAGE_STAFF);
        set.add(MANAGE_ADMINS);
        set.add(MANAGE_OWNERS);
        set.add(MANAGE_DESIGNATIONS);
        set.add(MANAGE_PERMISSIONS);
        set.add(TRANSFER_OWNERSHIP);
        return Collections.unmodifiableSet(set);
    }

    public static Set<String> defaultOwnerPermissions() {
        Set<String> set = new LinkedHashSet<>();
        set.add(VIEW_DASHBOARD);
        set.add(MANAGE_PRODUCTS);
        set.add(MANAGE_CATEGORIES);
        set.add(MANAGE_INVENTORY);
        set.add(MANAGE_ORDERS);
        set.add(MANAGE_CUSTOMERS);
        set.add(MANAGE_DELIVERY);
        set.add(VIEW_REPORTS);
        set.add(MANAGE_STAFF);
        set.add(MANAGE_DESIGNATIONS);
        return Collections.unmodifiableSet(set);
    }

    public static Set<String> defaultAdminPermissions() {
        Set<String> set = new LinkedHashSet<>();
        set.add(VIEW_DASHBOARD);
        set.add(MANAGE_PRODUCTS);
        set.add(MANAGE_CATEGORIES);
        set.add(MANAGE_INVENTORY);
        set.add(MANAGE_ORDERS);
        set.add(MANAGE_CUSTOMERS);
        set.add(MANAGE_DELIVERY);
        set.add(VIEW_REPORTS);
        set.add(MANAGE_STAFF);
        set.add(MANAGE_DESIGNATIONS);
        return Collections.unmodifiableSet(set);
    }

    public static Set<String> defaultStaffPermissions() {
        Set<String> set = new LinkedHashSet<>();
        set.add(VIEW_DASHBOARD);
        set.add(MANAGE_ORDERS);
        set.add(MANAGE_INVENTORY);
        set.add(MANAGE_DELIVERY);
        return Collections.unmodifiableSet(set);
    }

    public static Set<String> defaultDeliveryPermissions() {
        Set<String> set = new LinkedHashSet<>();
        set.add(MANAGE_DELIVERY);
        return Collections.unmodifiableSet(set);
    }
}
