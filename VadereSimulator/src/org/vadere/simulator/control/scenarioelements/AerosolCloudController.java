package org.vadere.simulator.control.scenarioelements;

import org.vadere.state.attributes.scenario.AttributesAerosolCloud;
import org.vadere.state.scenario.*;
import org.vadere.util.logging.Logger;


/**
 * Manipulate pedestrians which enter the given {@link AerosolCloud}.
 * <p>
 * Take following attributes into account when manipulating pedestrians:
 * - {@link AttributesAerosolCloud#getRadius()} ()}
 * <p>
 */

public class AerosolCloudController extends ScenarioElementController {

    private static final Logger log = Logger.getLogger(AerosolCloudController.class);

    public final AerosolCloud aerosolCloud;
    private Topography topography;

    // Constructors
    public AerosolCloudController(Topography topography, AerosolCloud aerosolCloud) {
        this.aerosolCloud = aerosolCloud;
        this.topography = topography;
    }

    // Other methods
    public void update(double simTimeInSec) {
        // controller hinzufügen, entfernen
        // postition verändern
        // cloud vergrößern
        // viruslast anpassen
    }


//    private void notifyListenersAerosolCloudReached(final Pedestrian pedestrian) {
//        for (AerosolCloudListener listener : aerosolCloud.getAerosolCloudListeners()) {
//            listener.reachedAerosolCloud(aerosolCloud, pedestrian);
//        }
//    }
}
