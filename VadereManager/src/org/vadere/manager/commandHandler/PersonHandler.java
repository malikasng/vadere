package org.vadere.manager.commandHandler;

import org.vadere.manager.stsc.TraCICmd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Experimental annotation interface which removes long manually create switch statements by
 * creating a dynamic HashMap connecting commands(using variableIDs) to the corresponding
 * handler methods.
 *
 * Reflection is minimized to a single startup routine at object creation. At runtime only
 * HashMap access is performed.
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersonHandler {
	TraCICmd commandIdentifier();
	TraCIPersonVar variable();
	String clientCommandName();
}