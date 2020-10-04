/*
 * Cerberus-Net is a simple network library based on the java socket
 * framework. It also includes a powerful scheduling solution.
 * Visit https://cerberustek.com for more details
 * Copyright (c)  2020  Adrian Paskert
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. See the file LICENSE included with this
 * distribution for more information.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.cerberustek.packet.impl;

import com.cerberustek.*;
import com.cerberustek.channel.NetValve;
import com.cerberustek.channel.impl.MetaReplChannel;
import com.cerberustek.data.DiscriminatorMap;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.impl.elements.ContainerElement;
import com.cerberustek.data.impl.elements.DocElement;
import com.cerberustek.data.impl.elements.EncryptionElement;
import com.cerberustek.data.impl.elements.PublicKeyElement;
import com.cerberustek.data.impl.tags.ContainerTag;
import com.cerberustek.data.impl.tags.EncryptionTag;
import com.cerberustek.event.Event;
import com.cerberustek.event.EventHandler;
import com.cerberustek.event.EventListener;
import com.cerberustek.events.*;
import com.cerberustek.exceptions.NetSecurityException;
import com.cerberustek.settings.Settings;
import com.cerberustek.cipher.impl.RSAPrivateCipher;
import com.cerberustek.cipher.impl.RSAPublicCipher;
import com.cerberustek.exception.LoadFormatException;
import com.cerberustek.exception.NoMatchingDiscriminatorException;
import com.cerberustek.exception.UnknownDiscriminatorException;
import com.cerberustek.packet.ChannelEncryption;
import com.cerberustek.packet.NetSecurity;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.UUID;

@EventHandler(events = {
        NetDataReceptionEvent.class,
        NetRequestReceptionEvent.class
})
public class HostNetSecurity implements NetSecurity, EventListener {

    private final HashMap<Short, ChannelEncryption> encryptions = new HashMap<>();
    private final NetValve valve;

    private DiscriminatorMap authChannelDiscriminators;
    private DiscriminatorMap secureChannelDiscriminators;

    private UUID own;
    private UUID other;

    private MetaReplChannel authChannel;
    private MetaReplChannel secureChannel;

    private RSAPublicCipher verifyCipher;
    private RSAPrivateCipher signCipher;

    private Settings settings;

    private CerberusEncrypt encryptService;
    private CerberusEvent eventService;

    public HostNetSecurity(NetValve valve) {
        this.valve = valve;
    }

    @Override
    public boolean isHost() {
        return true;
    }

    @Override
    public UUID otherKey() {
        return other;
    }

    @Override
    public UUID ownKey() {
        return own;
    }

    @Override
    public MetaData verify(MetaData data) throws NetSecurityException {
        if (other == null)
            throw new NetSecurityException("No key present");

        RSAPublicKey pup = getEncrypt().getRSAPublicKey(other);
        return null;
    }

    @Override
    public MetaData sign(MetaData data) {
        return null;
    }

    @Override
    public EncryptionElement encrypt(ChannelEncryption encryption, MetaData data) {
        return null;
    }

    @Override
    public MetaData decrypt(ChannelEncryption encryption, EncryptionElement element) {
        return null;
    }

    @Override
    public ChannelEncryption newChannelEncryption(short channelId) {
        return null;
    }

    @Override
    public ChannelEncryption getChannelEncryption(short channelId) {
        return null;
    }

    @Override
    public boolean hasEncryption(short channelId) {
        return false;
    }

    @Override
    public void deleteEncryption(short channelId) {

    }

    @Override
    public boolean isValid(ChannelEncryption encryption) {
        return false;
    }

    @Override
    public void renew(short channelId) {

    }

    @Override
    public void renewAll() {

    }

    @Override
    public int keyUptime() {
        return settings.getInteger("net_key_uptime", 30000);
    }

    @Override
    public void destroy() {
        if (authChannel != null)
            authChannel.stop();
        if (secureChannel != null)
            secureChannel.stop();
    }

    /**
     * Returns the auth network channel.
     *
     * If the channel is currently not loaded, this method will load the
     * channel before returning it.
     *
     * @return auth channel
     */
    private MetaReplChannel authChannel() {
        if (authChannel != null)
            return authChannel;

        authChannel = new MetaReplChannel(settings.getSignedShort("net_auth_channel", DEFAULT_AUTH_CHANNEL),
                valve, loadAuthChannelDiscriminators());
        authChannel.start();

        return authChannel;
    }

    /**
     * Returns the secure network channel.
     *
     * If the channel is currently not loaded, this method will load the
     * channel before returning it.
     *
     * @return secure channel
     */
    private MetaReplChannel secureChannel() {
        if (secureChannel != null)
            return secureChannel;

        secureChannel = new MetaReplChannel(settings.getSignedShort("net_secure_channel", DEFAULT_SECURE_CHANNEL),
                valve, loadSecureChannelDiscriminators());
        secureChannel.start();

        return secureChannel;
    }

    /**
     * Returns the discriminator map for the auth data network channel.
     *
     * If the auth channel discriminators are currently not loaded, this
     * method will load the discriminators and return them.
     *
     * @return auth channel discriminators
     */
    private DiscriminatorMap loadAuthChannelDiscriminators() {
        if (authChannelDiscriminators != null)
            return authChannelDiscriminators;

        authChannelDiscriminators = CerberusData.readDiscriminatorMap(
                settings.getString("net_auth_discriminators", "config/net/auth.cdf"));
        return authChannelDiscriminators;
    }

    /**
     * Returns the discriminator map for the secure data network channel.
     *
     * If the secure channel discriminators are currently not loaded, this
     * method will load the discriminators and return them.
     *
     * @return secure channel discriminators
     */
    private DiscriminatorMap loadSecureChannelDiscriminators() {
        if (secureChannelDiscriminators != null)
            return secureChannelDiscriminators;

        secureChannelDiscriminators = CerberusData.readDiscriminatorMap(
                settings.getString("net_secure_discriminators", "config/net/secure.cdf"));
        return secureChannelDiscriminators;
    }

    @Override
    public MetaData convert() {
        return null;
    }

    @Override
    public void load(MetaData metaData) throws LoadFormatException {

    }

    /**
     * Returns the current instance of the cerberus encrypt service.
     * @return encrypt service instance
     */
    private CerberusEncrypt getEncrypt() {
        if (encryptService == null)
            encryptService = CerberusRegistry.getInstance().getService(CerberusEncrypt.class);
        return encryptService;
    }

    /**
     * Returns the current instance of the cerberus event service.
     * @return event service instance
     */
    private CerberusEvent getEventService() {
        if (eventService == null)
            eventService = CerberusRegistry.getInstance().getService(CerberusEvent.class);
        return eventService;
    }

    private boolean verify(ContainerElement data, ContainerElement signature) {
        if (verifyCipher == null)
            return false;
        return verifyCipher.verifySignature(signature.get(), data.get());
    }

    /**
     * Will update the verification cipher from the stored private key
     * of the net security instance.
     */
    private void updateVerifyCipher() {
        PublicKey key = getEncrypt().getPublicKey(other);
        if (key != null)
            updateVerifyCipher(key);
    }

    private void updateVerifyCipher(PublicKey key) {
        try {
            verifyCipher = new RSAPublicCipher((RSAPublicKey) key);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            CerberusRegistry.getInstance().critical("Unable to create RSA cipher: " + e.getMessage());
            getEventService().executeFullEIF(new ExceptionEvent(CerberusNet.class, e));
        }
    }

    /**
     * Will update the verification cipher from the stored public key
     * of the net security instance.
     */
    private void updateSignCipher() {
        PrivateKey key = getEncrypt().getPrivateKey(own);
        if (key != null)
            updateSignCipher(key);
    }

    private void updateSignCipher(PrivateKey key) {
        try {
            signCipher = new RSAPrivateCipher((RSAPrivateKey) key);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            CerberusRegistry.getInstance().critical("Unable to create RSA cipher: " + e.getMessage());
            getEventService().executeFullEIF(new ExceptionEvent(CerberusNet.class, e));
        }
    }

    /**
     * Will handle an incoming authentication data package.
     * @param doc auth data package
     */
    private void handleAuth(DocElement doc) throws UnknownDiscriminatorException {
        ContainerTag data_ = doc.extract("data", ContainerTag.class);
        ContainerTag signature_ = doc.extract("signature", ContainerTag.class);


        if (data_ == null || signature_ == null)
            return;

        MetaData data = data_.get(loadAuthChannelDiscriminators());

        if (data instanceof PublicKeyElement) {
            PublicKeyElement pup = (PublicKeyElement) data;
            if (pup.get() == null)
                return;

            if (other == null) {
                updateVerifyCipher(pup.get());
                if (!verify(data_, signature_)) {
                    CerberusRegistry.getInstance().critical("Authentication failed: signature does not match" +
                            " public key. Possible man-in-the-middle-attack");
                    CerberusRegistry.getInstance().critical("Forcefully shutting down connection...");
                    getEventService().executeFullEIF(new NetAuthenticationFailEvent(this));
                    valve.stop();
                }

                other = getEncrypt().registerPublicKey(pup.get());
            } else {
                PublicKey key = getEncrypt().getPublicKey(other);
                if (key == null) {
                    updateVerifyCipher(pup.get());
                    if (!verify(data_, signature_)) {
                        CerberusRegistry.getInstance().critical("Authentication failed: signature does not match" +
                                " public key. Possible man-in-the-middle-attack");
                        CerberusRegistry.getInstance().critical("Forcefully shutting down connection...");
                        getEventService().executeFullEIF(new NetAuthenticationFailEvent(this));
                        valve.stop();
                    }

                    getEncrypt().registerPublicKey(other, pup.get());
                }
                else {
                    if (!key.equals(pup.get())) {
                        CerberusRegistry.getInstance().critical("Identity of client changed! Possible" +
                                " man-in-the-middle-attack");
                        CerberusRegistry.getInstance().critical("Forcefully shutting down connection...");
                        getEventService().executeFullEIF(new NetIdentityChangeEvent(other, pup.get()));
                        valve.stop();
                    } // else all good, nothing new
                }
            }
        }
    }

    private MetaData formatSecure(MetaData message) throws NoMatchingDiscriminatorException {
        EncryptionTag data = new EncryptionTag("data");
        data.set(message, verifyCipher, loadSecureChannelDiscriminators());
        ContainerTag signature = new ContainerTag("signature", signCipher.sign(data.get()));

        DocElement doc = new DocElement();
        doc.insert(data);
        doc.insert(signature);

        return doc;
    }

    private MetaData retrieveSecure(MetaData raw) throws UnknownDiscriminatorException {
        if (!(raw instanceof DocElement))
            return null;
        return retrieveSecure((DocElement) raw);
    }

    private MetaData retrieveSecure(DocElement doc) throws UnknownDiscriminatorException {
        EncryptionTag data = doc.extract("data", EncryptionTag.class);
        ContainerTag signature = doc.extract("signature", ContainerTag.class);


        if (data == null || signature == null)
            return null;

        if (!verifyCipher.verifySignature(signature.get(), data.get())) {
            CerberusRegistry.getInstance().critical("Authentication failed: signature does not match" +
                    " public key. Possible man-in-the-middle-attack");
            CerberusRegistry.getInstance().critical("Forcefully shutting down connection...");
            getEventService().executeFullEIF(new NetAuthenticationFailEvent(this));
            valve.stop();
        }

        return data.get(signCipher, loadSecureChannelDiscriminators());
    }

    /**
     * Will handle an incoming secure data package.
     * @param doc encrypted secure data
     */
    private void handleSecure(DocElement doc) throws UnknownDiscriminatorException {
        MetaData message = retrieveSecure(doc);
        // TODO
    }

    private MetaData formatAuth(MetaData message) throws NoMatchingDiscriminatorException {
        ContainerTag data = new ContainerTag("data", new byte[0]);
        data.set(message, loadAuthChannelDiscriminators());

        ContainerTag signature = new ContainerTag("signature", signCipher.genSignature(data.get()));
        DocElement doc = new DocElement();
        doc.insert(data);
        doc.insert(signature);

        return doc;
    }

    private MetaData handleAuthRequest(MetaData request) throws NoMatchingDiscriminatorException {
        PublicKey key = getEncrypt().getPublicKey(own);
        if (key == null) {
            CerberusRegistry.getInstance().critical("Server cannot authenticate itself; Missing key-set");
            return null;
        }
        return formatAuth(new PublicKeyElement(key));
    }

    private MetaData handleSecureRequest(MetaData request) {
        return null;
    }

    @Override
    public boolean onEvent(Event event) {
        if (event instanceof NetRequestReceptionEvent) {
            if (((NetRequestReceptionEvent) event).getReplyChannel().getChannelId() == authChannel.getChannelId()) {
                // TODO handle auth request
                return true;
            } else if (((NetRequestReceptionEvent) event).getReplyChannel().getChannelId() == secureChannel.getChannelId()) {
                // TODO handle secure request
                return true;
            }

            return false;
        } else if (event instanceof NetDataReceptionEvent) {
            if (((NetDataReceptionEvent) event).getChannel().getChannelId() == authChannel.getChannelId()) {
                // TODO handle auth package
                return true;
            } else if (((NetDataReceptionEvent) event).getChannel().getChannelId() == secureChannel.getChannelId()) {
                // TODO handle secure package
                return true;
            }

            return false;
        } else
            return false;
    }
}
