/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.hapi.utils.keys;

import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.relocatedIfNotPresentInWorkingDir;
import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.relocatedIfNotPresentWithCurrentPathPrefix;
import static com.swirlds.common.utility.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.nio.file.Paths;
import java.security.KeyPair;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ed25519UtilsTest {
    private static final String devGenesisPemLoc = "src/test/resources/vectors/genesis.pem";
    private static final String devGenesisPassphrase = "swirlds";

    // The well-known Ed25519 key used to bootstrap a development network; has ZERO sensitivity or
    // security impact
    final String devPrivateKey = "91132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";
    final String devPublicKey = "0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

    @TempDir
    private File tempDir;

    @Test
    void throwsIllegalArgumentOnMissingTargetLoc() {
        final var tmpLoc = "unreachable/missing/re-encrypted_genesis.pem";
        final var tmpPassphrase = "hedera";
        final var seed = CommonUtils.unhex(devPrivateKey);

        assertThrows(IllegalArgumentException.class, () -> Ed25519Utils.writeKeyTo(seed, tmpLoc, tmpPassphrase));
    }

    @Test
    void canRelocateFromWhenFileIsMissing() {
        final var missingLoc = "test/resources/vectors/genesis.pem";

        final var relocated =
                relocatedIfNotPresentWithCurrentPathPrefix(new File(missingLoc), "test", "src" + File.separator);

        assertEquals("src/test/resources/vectors/genesis.pem", relocated.getPath());
    }

    @Test
    void doesNotRelocateIfSegmentMissing() {
        final var missingLoc = "test/resources/vectors/genesis.pem";

        final var relocated =
                relocatedIfNotPresentWithCurrentPathPrefix(new File(missingLoc), "NOPE", "src" + File.separator);

        assertEquals(missingLoc, relocated.getPath());
    }

    @Test
    void triesToRelocateObviouslyMissingPath() {
        final var notPresent = Paths.get("nowhere/src/main/resources/nothing.txt");

        final var expected = Paths.get("hedera-node/test-clients/src/main/resources/nothing.txt");

        final var actual = relocatedIfNotPresentInWorkingDir(notPresent);

        assertEquals(expected, actual);
    }

    @Test
    void writesRecoverableDevGenesisKey() {
        final var tmpLoc = tempDir.getPath() + File.separator + "re-encrypted_genesis.pem";
        final var tmpPassphrase = "hedera";

        Ed25519Utils.writeKeyTo(CommonUtils.unhex(devPrivateKey), tmpLoc, tmpPassphrase);
        final var recovered = Ed25519Utils.readKeyFrom(tmpLoc, tmpPassphrase);
        assertIsGenesisDevKey(recovered);

        assertTrue(new File(tmpLoc).delete());
    }

    @Test
    void recoversDevGenesisKeyPair() {
        final var recovered = Ed25519Utils.readKeyPairFrom(new File(devGenesisPemLoc), devGenesisPassphrase);

        assertIsGenesisDevKeyPair(recovered);
    }

    @Test
    void recoversDevGenesisKey() {
        final var recovered = Ed25519Utils.readKeyFrom(devGenesisPemLoc, devGenesisPassphrase);

        assertIsGenesisDevKey(recovered);
    }

    @Test
    void failsOnMissingPem() {
        final var notDevGenesisPemLoc = "src/test/resources/vectors/missing.pem";
        assertThrows(
                IllegalArgumentException.class,
                () -> Ed25519Utils.readKeyFrom(notDevGenesisPemLoc, devGenesisPassphrase));
    }

    @Test
    void failsOnBadPassphrase() {
        final var notDevGenesisPassphrase = "sdlriws";
        assertThrows(
                IllegalArgumentException.class,
                () -> Ed25519Utils.readKeyFrom(devGenesisPemLoc, notDevGenesisPassphrase));
    }

    private void assertIsGenesisDevKey(final EdDSAPrivateKey key) {
        assertEquals(devPublicKey, hex(key.getAbyte()));
        assertEquals(devPrivateKey, hex(key.getSeed()));
    }

    private void assertIsGenesisDevKeyPair(final KeyPair keyPair) {
        assertTrue(hex(keyPair.getPublic().getEncoded()).endsWith(devPublicKey));
        assertTrue(hex(keyPair.getPrivate().getEncoded()).endsWith(devPrivateKey));
    }
}
