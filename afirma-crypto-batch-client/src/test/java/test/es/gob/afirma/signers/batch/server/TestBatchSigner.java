/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package test.es.gob.afirma.signers.batch.server;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.signers.batch.client.BatchSigner;
import es.gob.afirma.test.support.RequiresTriphaseServer;

/** Pruebas del cliente de firma por lote.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
public final class TestBatchSigner {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final String CERT_PATH = "PFActivoFirSHA1.pfx"; //$NON-NLS-1$
	private static final String CERT_PASS = "12341234"; //$NON-NLS-1$
	private static final String CERT_ALIAS = "fisico activo prueba"; //$NON-NLS-1$

	private static final String PRE_URL = "http://localhost:8080/afirma-server-triphase-signer/BatchPresigner"; //$NON-NLS-1$
	private static final String POST_URL = "http://localhost:8080/afirma-server-triphase-signer/BatchPostsigner"; //$NON-NLS-1$

	/** Construye el lote de firma, guardando cada firma en un directorio temporal.
	 * @return XML del lote de firma.
	 * @throws Exception En cualquier error. */
	private static String buildBatchXml() throws Exception {
		final File outDir = Files.createTempDirectory("afirma-batch").toFile(); //$NON-NLS-1$
		return
			"<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n" + //$NON-NLS-1$
			"<signbatch stoponerror=\"false\" algorithm=\"SHA256\">\r\n" + //$NON-NLS-1$
			" <singlesign Id=\"7725374e-728d-4a33-9db9-3a4efea4cead\">\r\n" + //$NON-NLS-1$
			"  <datasource>SG9sYSBNdW5kbw==</datasource>\r\n" + //$NON-NLS-1$
			"  <format>XAdES</format>\r\n" + //$NON-NLS-1$
			"  <suboperation>sign</suboperation>\r\n" + //$NON-NLS-1$
			"  <extraparams>U2lnbmF0dXJlSWQ9NzcyNTM3NGUtNzI4ZC00YTMzLTlkYjktM2E0ZWZlYTRjZWFk</extraparams>\r\n" + //$NON-NLS-1$
			"  <signsaver>\r\n" + //$NON-NLS-1$
			"   <class>es.gob.afirma.signers.batch.SignSaverFile</class>\r\n" + //$NON-NLS-1$
			"   <config>" + encodeFileName(outDir, "FIRMA1.xml") + "</config>\r\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"  </signsaver>\r\n" + //$NON-NLS-1$
			" </singlesign>\r\n" + //$NON-NLS-1$
			" <singlesign Id=\"93d1531c-cd32-4c8e-8cc8-1f1cfe66f64a\">\r\n" + //$NON-NLS-1$
			"  <datasource>SG9sYSBNdW5kbw==</datasource>\r\n" + //$NON-NLS-1$
			"  <format>CAdES</format>\r\n" + //$NON-NLS-1$
			"  <suboperation>sign</suboperation>\r\n" + //$NON-NLS-1$
			"  <extraparams>U2lnbmF0dXJlSWQ9OTNkMTUzMWMtY2QzMi00YzhlLThjYzgtMWYxY2ZlNjZmNjRh</extraparams>\r\n" + //$NON-NLS-1$
			"  <signsaver>\r\n" + //$NON-NLS-1$
			"   <class>es.gob.afirma.signers.batch.SignSaverFile</class>\r\n" + //$NON-NLS-1$
			"   <config>" + encodeFileName(outDir, "FIRMA2.xml") + "</config>\r\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"  </signsaver>\r\n" + //$NON-NLS-1$
			" </singlesign>\r\n" + //$NON-NLS-1$
			"</signbatch>"; //$NON-NLS-1$
	}

	private static String encodeFileName(final File dir, final String name) {
		return Base64.encode(
			("FileName=" + new File(dir, name).getAbsolutePath()).getBytes(StandardCharsets.UTF_8) //$NON-NLS-1$
		);
	}

	/** Prueba simple del cliente de firma por lotes.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresTriphaseServer.class)
	public void testBatchSign() throws Exception {

		final String batchXml = buildBatchXml();

		final PrivateKeyEntry pke;
		final KeyStore ks = KeyStore.getInstance("PKCS12"); //$NON-NLS-1$
		try(
			final InputStream is = ClassLoader.getSystemResourceAsStream(CERT_PATH)
		) {
			ks.load(
				is,
				CERT_PASS.toCharArray()
			);
		}
		pke = (PrivateKeyEntry) ks.getEntry(CERT_ALIAS, new KeyStore.PasswordProtection(CERT_PASS.toCharArray()));

		final String res = BatchSigner.signXML(
			batchXml.getBytes(StandardCharsets.UTF_8),
			PRE_URL,
			POST_URL,
			pke.getCertificateChain(),
			pke.getPrivateKey()
		);

		Assert.assertNotNull("El servidor no ha devuelto resultado para el lote", res); //$NON-NLS-1$
		Assert.assertFalse("El servidor ha devuelto un resultado vacio para el lote", res.trim().isEmpty()); //$NON-NLS-1$
		LOGGER.info(res);
	}

}
