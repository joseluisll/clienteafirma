package es.gob.afirma.keystores.mozilla;

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.keystores.mozilla.bintutil.ElfMachineType;
import es.gob.afirma.keystores.mozilla.bintutil.ElfParser;

/** Pruebas de analizado de ficheros ELF.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s Capote. */
public final class TestBinParser {

	private static ElfMachineType getMachineTypeFromResource(final String resource) throws Exception {
		try (
			final InputStream is = TestBinParser.class.getResourceAsStream(resource);
		) {
			return ElfParser.getMachineType(AOUtil.getDataFromInputStream(is));
		}
	}

	/** Prueba de an&aacute;lisis de un fichero ELF de ARM de 64 bits.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	public void testElfArm64() throws Exception {
		Assert.assertEquals(ElfMachineType.ARM64, getMachineTypeFromResource("/elf_arm64")); //$NON-NLS-1$
	}

	/** Prueba de an&aacute;lisis de un fichero ELF de x86 de 64 bits.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	public void testElfX64() throws Exception {
		Assert.assertEquals(ElfMachineType.AMD64, getMachineTypeFromResource("/elf_x64")); //$NON-NLS-1$
	}

	/** Prueba de an&aacute;lisis de un fichero ELF de x86 de 32 bits.
	 * @throws Exception En cualquier error. */
	@SuppressWarnings("static-method")
	@Test
	public void testElfX86() throws Exception {
		Assert.assertEquals(ElfMachineType.X86, getMachineTypeFromResource("/elf_x86")); //$NON-NLS-1$
	}

}
