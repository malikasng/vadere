package org.vadere.gui.onlinevisualization.control;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.vadere.gui.components.model.DefaultSimulationConfig;
import org.vadere.gui.components.model.SimulationModel;
import org.vadere.gui.components.utils.Resources;
import org.vadere.gui.components.view.SimulationRenderer;
import org.vadere.gui.onlinevisualization.view.IRendererChangeListener;
import org.vadere.gui.postvisualization.PostVisualisation;
import org.vadere.gui.postvisualization.utils.SVGGenerator;
import org.vadere.gui.postvisualization.utils.TikzGenerator;

import javax.swing.*;

import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.Preferences;

public class ActionGenerateTikz extends AbstractAction implements IRendererChangeListener {
	private static Logger logger = LogManager.getLogger(ActionGenerateTikz.class);
	private static Resources resources = Resources.getInstance("postvisualization");
	private final TikzGenerator tikzGenerator;
	private final SimulationModel<? extends DefaultSimulationConfig> model;

	public ActionGenerateTikz(final String name, final Icon icon, final SimulationRenderer renderer,
							  final SimulationModel<? extends DefaultSimulationConfig> model) {
		super(name, icon);
		this.tikzGenerator = new TikzGenerator(renderer, model);
		this.model = model;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Date todaysDate = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat(resources.getProperty("View.dataFormat"));
		String formattedDate = formatter.format(todaysDate);

		JFileChooser fileChooser = new JFileChooser(Preferences.userNodeForPackage(PostVisualisation.class).get("PostVis.snapshotDirectory.path", "."));
		File outputFile = new File("pv_snapshot_" + formattedDate + ".tex");

		fileChooser.setSelectedFile(outputFile);

		int returnVal = fileChooser.showDialog(null, "Save");

		if (returnVal == JFileChooser.APPROVE_OPTION) {

			outputFile = fileChooser.getSelectedFile().toString().endsWith(".tex") ? fileChooser.getSelectedFile()
					: new File(fileChooser.getSelectedFile().toString() + ".tex");

			boolean completeDocument = true;
			tikzGenerator.generateTikz(outputFile, completeDocument);
		}
	}

	@Override
	public void update(SimulationRenderer renderer) {
	}
}