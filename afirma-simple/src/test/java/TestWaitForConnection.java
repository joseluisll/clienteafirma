import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.test.support.RequiresNetwork;

/** Prueba de apertura del puerto local de AutoFirma a la espera de una conexi&oacute;n
 * entrante, para comprobar si el cortafuegos la bloquea desde el exterior.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s.*/
public final class TestWaitForConnection {

	private static final int PORT = 6629;

	/** Tiempo de espera de una conexi&oacute;n entrante. La versi&oacute;n original de esta
	 * prueba esperaba indefinidamente a que un cliente externo se conectase; aqu&iacute; la
	 * espera est&aacute; acotada para que la prueba no pueda bloquear la ejecuci&oacute;n. */
	private static final int ACCEPT_TIMEOUT_MS = 10000;

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	/** Comprueba que el puerto local de AutoFirma puede abrirse y quedar a la escucha.
	 * Si ning&uacute;n cliente se conecta antes del tiempo l&iacute;mite se considera
	 * correcto: lo que se comprueba es que el puerto pueda abrirse.
	 * @throws Exception En cualquier error no controlado. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresNetwork.class)
	public void testOpenLocalPortAndWaitForConnection() throws Exception {
		final ServerSocketFactory ssf = ServerSocketFactory.getDefault();
		try (
			final ServerSocket ss = ssf.createServerSocket(PORT);
		) {
			Assert.assertTrue("El puerto " + PORT + " no ha quedado a la escucha", ss.isBound()); //$NON-NLS-1$ //$NON-NLS-2$
			ss.setSoTimeout(ACCEPT_TIMEOUT_MS);
			LOGGER.info("A la espera de una conexion en el puerto " + PORT); //$NON-NLS-1$
			try (
				final Socket s = ss.accept();
			) {
				LOGGER.info("Conexion recibida desde " + s.getRemoteSocketAddress()); //$NON-NLS-1$
			}
			catch (final SocketTimeoutException e) {
				LOGGER.info("No se ha recibido ninguna conexion en " + ACCEPT_TIMEOUT_MS + " ms"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		catch (final IOException e) {
			LOGGER.log(Level.SEVERE, "Error creando el servicio servidor en el puerto " + PORT, e); //$NON-NLS-1$
			Assert.fail("No se ha podido abrir el puerto " + PORT + ": " + e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

}
