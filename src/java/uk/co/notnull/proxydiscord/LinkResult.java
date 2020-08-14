package uk.co.notnull.proxydiscord;

public enum LinkResult {
    UNKNOWN_ERROR, //Exception occurred
    NO_TOKEN, //No token provided
    INVALID_TOKEN, //Invalid token provided
    NOT_VERIFIED, //Link successful, but missing verified role
    ALREADY_LINKED, //Account is already linked
    ALREADY_LINKED_NOT_VERIFIED, //Account is already linked, and also missing the verified role
    SUCCESS //Linked successfully
}
