package org.eclipse.cdt.debug.internal.core.model;

import org.eclipse.cdt.debug.core.CDebugCorePlugin;
import org.eclipse.cdt.debug.core.cdi.event.ICDIEvent;
import org.eclipse.cdt.debug.core.cdi.event.ICDIResumedEvent;
import org.eclipse.cdt.debug.core.cdi.model.ICDIObject;
import org.eclipse.cdt.debug.core.cdi.model.ICDIVariable;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;

/**
 *
 * Enter type comment.
 * 
 * @since: Oct 2, 2002
 */
public class CGlobalVariable extends CModificationVariable
{
	/**
	 * Constructor for CGlobalVariable.
	 * @param parent
	 * @param cdiVariable
	 */
	public CGlobalVariable( CDebugElement parent, ICDIVariable cdiVariable )
	{
		super( parent, cdiVariable );
	}

	/**
	 * Returns the current value of this variable. The value
	 * is cached.
	 * 
	 * @see org.eclipse.debug.core.model.IVariable#getValue()
	 */
	public IValue getValue() throws DebugException
	{
		if ( fValue == null )
		{
			fValue = CValueFactory.createGlobalValue( this, getCurrentValue() );
		}
		return fValue;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.debug.core.cdi.event.ICDIEventListener#handleDebugEvent(ICDIEvent)
	 */
	public void handleDebugEvent( ICDIEvent event )
	{
		super.handleDebugEvent( event );
	
		ICDIObject source = event.getSource();
		if (source == null)
			return;
	
		if ( source.getTarget().equals( getCDITarget() ) )
		{
			if ( event instanceof ICDIResumedEvent )
			{
				try
				{
					setChanged( false );
				}
				catch( DebugException e )
				{
					CDebugCorePlugin.log( e );
				}
			}
		}
	}
}
