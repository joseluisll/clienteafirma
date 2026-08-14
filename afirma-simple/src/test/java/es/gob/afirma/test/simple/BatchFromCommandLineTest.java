/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.test.simple;

import java.io.File;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.standalone.SimpleAfirma;
import es.gob.afirma.test.support.RequiresTriphaseServer;

/** Pruebas de firma por lotes desde l&iacute;nea de comandos.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s. */
public final class BatchFromCommandLineTest {

	private static final String TEST_FILE = "/batch-with-countersign.xml"; //$NON-NLS-1$
	private static final String ALIAS = "FISICO ACTIVO PRUEBA - 881146077"; //$NON-NLS-1$

	private static final String  PRE_URL = "http://localhost:8080/afirma-server-triphase-signer/BatchPresigner"; //$NON-NLS-1$
	private static final String POST_URL = "http://localhost:8080/afirma-server-triphase-signer/BatchPostsigner"; //$NON-NLS-1$

	/** Prueba de firma por lotes desde l&iacute;nea de comandos.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresTriphaseServer.class)
	@Ignore // Prueba manual: SimpleAfirma.main() invoca System.exit() y aborta la JVM de pruebas
	public void testBatchSignFromCommandLine() throws Exception {
		Assert.assertNotNull(
			"No se encuentra el lote de prueba " + TEST_FILE, //$NON-NLS-1$
			BatchFromCommandLineTest.class.getResource(TEST_FILE)
		);
		final String testFile = new File(BatchFromCommandLineTest.class.getResource(TEST_FILE).toURI()).getAbsolutePath();
		final String outFile = File.createTempFile("BATCH_", ".xml").getAbsolutePath();  //$NON-NLS-1$//$NON-NLS-2$
		SimpleAfirma.main(
			new String[] {
				"batchsign", //$NON-NLS-1$
				"-i", testFile, //$NON-NLS-1$
				"-o", outFile, //$NON-NLS-1$
				"-alias", ALIAS,  //$NON-NLS-1$
				"-preurl", PRE_URL, //$NON-NLS-1$
				"-posturl", POST_URL //$NON-NLS-1$
			}
		);
	}

}
