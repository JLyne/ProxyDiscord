package uk.co.notnull.proxydiscord;

public enum LinkResult {
    UNKNOWN_ERROR, //Exception occurred
    NO_TOKEN, //No token provided
    INVALID_TOKEN, //Invalid token provided
    ALREADY_LINKED, //Account is already linked
    SUCCESS //Linked successfully
}
