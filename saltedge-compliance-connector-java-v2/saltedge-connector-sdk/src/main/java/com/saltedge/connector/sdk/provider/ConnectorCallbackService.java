/*
 * @author Constantin Chelban (constantink@saltedge.com)
 * Copyright (c) 2020 Salt Edge.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.saltedge.connector.sdk.provider;

import com.saltedge.connector.sdk.SDKConstants;
import com.saltedge.connector.sdk.api.err.NotFound;
import com.saltedge.connector.sdk.api.services.tokens.ConfirmTokenService;
import com.saltedge.connector.sdk.api.services.tokens.RevokeTokenService;
import com.saltedge.connector.sdk.callback.mapping.SessionSuccessCallbackRequest;
import com.saltedge.connector.sdk.callback.services.SessionsCallbackService;
import com.saltedge.connector.sdk.callback.services.TokensCallbackService;
import com.saltedge.connector.sdk.models.persistence.Token;
import com.saltedge.connector.sdk.provider.models.ProviderOfferedConsents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Implementation of ProviderCallback interface
 * @see ConnectorCallbackAbs
 */
@Service
@Validated
public class ConnectorCallbackService implements ConnectorCallbackAbs {
    @Autowired
    private ConfirmTokenService confirmTokenService;
    @Autowired
    private RevokeTokenService revokeTokenService;
    @Autowired
    private SessionsCallbackService sessionsCallbackService;
    @Autowired
    private TokensCallbackService tokensCallbackService;

    /**
     * Provider notify Connector Module about oAuth success authentication and user consent for accounts
     *
     * @param sessionSecret of Token Create session
     * @param userId of authenticated User
     * @param accessToken is an unique string that identifies a user
     * @param accessTokenExpiresAt expiration time of accessToken (UTC time).
     * @param consents list of balances of accounts and transactions of accounts
     * @return returnUrl from token. Authorization page should redirect the browser to it.
     */
    @Override
    public String onAccountInformationAuthorizationSuccess(
            @NotEmpty String sessionSecret,
            @NotEmpty String userId,
            @NotEmpty String accessToken,
            @NotNull Instant accessTokenExpiresAt,
            @NotNull ProviderOfferedConsents consents
    ) {
        Token token = confirmTokenService.confirmToken(
                sessionSecret,
                userId,
                accessToken,
                accessTokenExpiresAt,
                consents
        );
        return (token == null) ? null : token.tppRedirectUrl;
    }

    /**
     * Provider should notify Connector Module about oAuth authentication fail
     *
     * @param sessionSecret of Token Create session
     * @return returnUrl from token
     */
    @Override
    public String onAccountInformationAuthorizationFail(@NotEmpty String sessionSecret) {
        Token token = revokeTokenService.revokeTokenBySessionSecret(sessionSecret);
        return (token == null) ? null : token.tppRedirectUrl;
    }

    /**
     * Revoke Account information consent associated with userId and accessToken
     *
     * @param userId unique identifier of authenticated User
     * @param accessToken unique string that identifies a user
     */
    @Override
    public boolean revokeAccountInformationConsent(
            @NotEmpty String userId,
            @NotEmpty String accessToken
    ) {
        Token token = revokeTokenService.revokeTokenByUserIdAndAccessToken(userId, accessToken);
        if (token != null && token.status == Token.Status.REVOKED) tokensCallbackService.sendRevokeTokenCallback(accessToken);
        return (token != null && token.status == Token.Status.REVOKED);
    }

    /**
     * Provider notify Connector Module about oAuth success authentication and user consent for payment
     *
     * @param paymentId of payment
     * @param userId of authenticated User
     * @param paymentExtra extra data of payment order
     * @return returnUrl for Payment authenticate session
     */
    @Override
    public String onPaymentInitiationAuthorizationSuccess(
            @NotEmpty String paymentId,
            @NotEmpty String userId,
            @NotEmpty Map<String, String> paymentExtra
    ) {
        String sessionSecret = paymentExtra.get(SDKConstants.KEY_SESSION_SECRET);
        SessionSuccessCallbackRequest params = new SessionSuccessCallbackRequest(userId, "ACTC");
        if (!StringUtils.isEmpty(sessionSecret)) sessionsCallbackService.sendSuccessCallback(sessionSecret, params);
        String returnToUrl = paymentExtra.get(SDKConstants.KEY_RETURN_TO_URL);
        return returnToUrl == null ? "" : returnToUrl;
    }

    /**
     * Provider should notify Connector Module about oAuth authentication fail or Payment confirmation deny
     *
     * @param paymentId of payment
     * @param paymentExtra extra data of payment order
     * @return returnUrl for Payment authenticate session
     */
    @Override
    public String onPaymentInitiationAuthorizationFail(@NotEmpty String paymentId, @NotEmpty Map<String, String> paymentExtra) {
        String sessionSecret = paymentExtra.get(SDKConstants.KEY_SESSION_SECRET);
        if (!StringUtils.isEmpty(sessionSecret)) sessionsCallbackService.sendFailCallback(sessionSecret, new NotFound.PaymentNotCreated());
        String returnToUrl = paymentExtra.get(SDKConstants.KEY_RETURN_TO_URL);
        return returnToUrl == null ? "" : returnToUrl;
    }
}
