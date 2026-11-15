package com.example.facturacion.services;

/** An explicit WSFE business rejection, distinct from an unconfirmed transport failure. */
public class ArcaRechazoException extends IllegalStateException {
    public ArcaRechazoException(String message) { super(message); }
}
