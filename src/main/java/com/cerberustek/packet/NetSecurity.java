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

package com.cerberustek.packet;

import com.cerberustek.Destroyable;
import com.cerberustek.data.MetaConvertible;
import com.cerberustek.data.MetaData;
import com.cerberustek.data.MetaLoadable;
import com.cerberustek.data.impl.elements.EncryptionElement;
import com.cerberustek.exceptions.NetSecurityException;

import java.util.UUID;

public interface NetSecurity extends Destroyable, MetaLoadable, MetaConvertible {

    short DEFAULT_AUTH_CHANNEL = 13;
    short DEFAULT_SECURE_CHANNEL = 14;

    /**
     * Returns rather this net security service is the host service.
     * @return is host service
     */
    boolean isHost();

    /**
     * Returns the uuid of the public key of the connection partner.
     * @return key uuid
     */
    UUID otherKey();

    /**
     * Returns the uuid of the private/public key pair of this network
     * instance.
     * @return private/public keypair id of this
     */
    UUID ownKey();

    /**
     * Will verify the specified meta data package
     * @param data data package
     * @return verified data
     * @throws NetSecurityException thrown, if the package could not be
     *              verified
     */
    MetaData verify(MetaData data) throws NetSecurityException;

    /**
     * Will sign the specified data package.
     * @param data data package
     * @return signed data package
     */
    MetaData sign(MetaData data);

    /**
     * Will encrypt the specified data package using the specified encryption
     * @param encryption encryption to use
     * @param data data to encrypt
     * @return encrypted data
     */
    EncryptionElement encrypt(ChannelEncryption encryption, MetaData data);

    /**
     * Will decrypt the specified data package using the specified encryption
     * @param encryption encryption to use
     * @param element element to decrypt
     * @return decrypted data
     */
    MetaData decrypt(ChannelEncryption encryption, EncryptionElement element);

    /**
     * Will create/request a new channel encryption.
     *
     * If the channel id already has a registered encryption, this
     * method will renew the encryption.
     *
     * @param channelId channel to get the encryption for
     * @return encryption
     */
    ChannelEncryption newChannelEncryption(short channelId);

    /**
     * Returns the channel encryption for the specified channel id.
     *
     * If there is no encryption registered for the specified channel
     * id, this method will simply return null.
     *
     * @param channelId channel id to the get encryption for.
     * @return encryption
     */
    ChannelEncryption getChannelEncryption(short channelId);

    /**
     * Returns rather or not an encryption is registered for the
     * specified channel id.
     * @param channelId channel id
     * @return has encrpytion
     */
    boolean hasEncryption(short channelId);

    /**
     * Will delete the encryption for the specified channel id.
     * @param channelId channel id
     */
    void deleteEncryption(short channelId);

    /**
     * Returns true, if the encryption of the specified channel
     * encryption is valid or not.
     * @param encryption is valid
     * @return is encryption valid
     */
    boolean isValid(ChannelEncryption encryption);

    /**
     * Will renew the encryption for the specified channel.
     *
     * If the specified channel does not have an encryption
     * registered, this method will do nothing.
     *
     * @param channelId channel id
     */
    void renew(short channelId);

    /**
     * Will renew all channel encryptions
     */
    void renewAll();

    /**
     * Returns the time duration in ms in which a key is valid after
     * creation.
     * @return valid duration
     */
    int keyUptime();
}
