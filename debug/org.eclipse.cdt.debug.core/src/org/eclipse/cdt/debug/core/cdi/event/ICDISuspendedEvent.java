/*
 *(c) Copyright QNX Software Systems Ltd. 2002.
 * All Rights Reserved.
 * 
 */

package org.eclipse.cdt.debug.core.cdi.event;

import org.eclipse.cdt.debug.core.cdi.ICDISessionObject;

/**
 * 
 * Notifies that the originator has been suspended. 
 * The originators:
 * <ul>
 * <li>target (ICDITarget)
 * <li>thread (ICDIThread)
 * </ul>
 * The reason of the suspension can be one of the following session 
 * objects:
 * <ul>
 * <li>breakpoint (ICDIBreakpoint)
 * <li>signal (ICDISignalReceived)
 * <li>end of the stepping range (ICDIEndSteppingRange)
 * </ul>
 * 
 * @since Jul 10, 2002
 */
public interface ICDISuspendedEvent extends ICDIEvent {

	/**
	 * Returns the session object that caused the suspension.
	 * 
	 * @return ICDIObject
	 */
	ICDISessionObject getReason();

}
