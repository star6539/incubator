package org.eclipse.equinox.console.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import java.io.Closeable;
import org.osgi.framework.BundleContext;

/**
 * This class implements functionality to disconnect from telnet or ssh console.
 */
public class DisconnectCommand {
	private static final String CLOSEABLE = "CLOSEABLE";
	private static final String DISCONNECT_MESSAGE = "Disconnect from console? (y/n; default=y) ";
	private static final String DISCONNECT_CONFIRMATION_Y = "y";
	
	private BundleContext context;
	
	public DisconnectCommand(BundleContext context) {
		this.context = context;
	}
	
	public void start() {
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put(CommandProcessor.COMMAND_SCOPE, "equinox");
		props.put(CommandProcessor.COMMAND_FUNCTION, new String[] {"disconnect"});
		context.registerService(DisconnectCommand.class.getName(), this, props);
	}
	
	public void disconnect(CommandSession session) {
		System.out.print(DISCONNECT_MESSAGE);
		System.out.flush();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String reply = null;
		try {
			reply = reader.readLine();
		} catch (IOException e) {
			System.out.println("Error while reading confirmation");
		}
		
		if (reply != null) {
			if (reply.toLowerCase().startsWith(DISCONNECT_CONFIRMATION_Y)) {
				Closeable closable = (Closeable)session.get(CLOSEABLE);
				if (closable != null) {
					try {
						closable.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					return;
				}
				
				session.close();
			}
		}
	}
}
