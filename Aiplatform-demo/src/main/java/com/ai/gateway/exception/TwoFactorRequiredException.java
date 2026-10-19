package com.ai.gateway.exception;

/** Signals that the password was valid but login must stop until a second factor is supplied. */
public class TwoFactorRequiredException extends RuntimeException {
    public TwoFactorRequiredException() {
        super("账户已启用两步验证，请输入动态验证码或备用码");
    }
}
