package com.example.racehorse_transport.validation;

public final class ContactPatterns {
    private ContactPatterns() {}

    public static final String PERSON_NAME = "^[\\p{L}\\p{M}]+(?:[ '\\u2019-][\\p{L}\\p{M}]+)*$";
    public static final String CONTACT_NAME = "^(?=.*[\\p{L}])[\\p{L}\\p{M}0-9]+(?:[ .,'&()/+-][\\p{L}\\p{M}0-9]+)*$";
    public static final String REGIONAL_PHONE = "^(?:(?:\\+?84|0)[\\s().-]*(?:3|5|7|8|9)(?:[\\s().-]*\\d){8}|(?:\\+?855|0)[\\s().-]*(?:1|6|7|8|9)(?:[\\s().-]*\\d){7,8}|(?:\\+?856|0)[\\s().-]*20(?:[\\s().-]*\\d){8})$";
    public static final String OPTIONAL_REGIONAL_PHONE = "^(?:|(?:\\+?84|0)[\\s().-]*(?:3|5|7|8|9)(?:[\\s().-]*\\d){8}|(?:\\+?855|0)[\\s().-]*(?:1|6|7|8|9)(?:[\\s().-]*\\d){7,8}|(?:\\+?856|0)[\\s().-]*20(?:[\\s().-]*\\d){8})$";
    public static final String EMAIL_WITH_DOMAIN = "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$";
}
