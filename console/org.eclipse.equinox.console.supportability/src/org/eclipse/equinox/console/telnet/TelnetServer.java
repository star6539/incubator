/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP AG and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Lazar Kirchev, SAP AG - initial API and implementation  
 *     IBM Corporation - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.console.telnet;

import java.io.IOException;
import java.net.*;
import org.apache.felix.service.command.CommandProcessor;

/**
 * A telnet server, which listens for telnet connections and starts a telnet connection manager
 * when a connection is accepted.
 *
 */
public class TelnetServer extends Thread {
	private ServerSocket server;
    private boolean isRunning = true;
    private CommandProcessor processor;
    
    public TelnetServer(CommandProcessor processor, String host, int port) throws IOException{
    	this.processor = processor;
    	if(host != null) {
    		server = new ServerSocket(port, 0, InetAddress.getByName(host));
    	} else {
    		server = new ServerSocket(port);
    	}
    }
    
	public void run()
    {
        try
        {
            while (isRunning)
            {
                final Socket socket = server.accept();
                TelnetConnection telnetConnection = new TelnetConnection(socket, processor);
                telnetConnection.start();
            }
        } catch (IOException e) {
            if (isRunning == true) {
                e.printStackTrace();
            }
        } finally {
        	isRunning = false;
            try {
                if (server != null) {
                    server.close();
                }
            } catch (IOException e){
            	// do nothing
            }
        }
    }
	
	public synchronized void stopTelnetServer() {
		isRunning = false;
		try {
            if (server != null) {
                server.close();
            }
        } catch (IOException e){
        	// do nothing
        }
		this.interrupt();
	}
}