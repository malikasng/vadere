package org.vadere.meshing.examples;

import org.jetbrains.annotations.NotNull;
import org.vadere.meshing.mesh.impl.PMeshPanel;
import org.vadere.meshing.mesh.impl.PSLG;
import org.vadere.meshing.mesh.triangulation.EdgeLengthFunctionApprox;
import org.vadere.meshing.mesh.triangulation.improver.eikmesh.impl.PEikMesh;
import org.vadere.meshing.utils.io.poly.PSLGGenerator;
import org.vadere.meshing.utils.io.tex.TexGraphGenerator;
import org.vadere.util.geometry.shapes.VPolygon;
import org.vadere.util.geometry.shapes.VRectangle;
import org.vadere.util.math.IDistanceFunction;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public class EikMeshPoly {
	private static final Color lightBlue = new Color(0.8584083044982699f, 0.9134486735870818f, 0.9645674740484429f);


	public static void main(String... args) throws InterruptedException, IOException {
		meshPoly("/poly/bridge.poly");
		//meshPoly("/poly/room.poly");
		//meshPoly("/poly/corner.poly");
	}

	public static void meshPoly(@NotNull final String fileName) throws IOException, InterruptedException {
		final InputStream inputStream = MeshExamples.class.getResourceAsStream(fileName);
		PSLG pslg = PSLGGenerator.toPSLGtoVShapes(inputStream);
		EdgeLengthFunctionApprox edgeLengthFunctionApprox = new EdgeLengthFunctionApprox(pslg, p -> 2.0);
		edgeLengthFunctionApprox.printPython();


		Collection<VPolygon> holes = pslg.getHoles();
		VPolygon segmentBound = pslg.getSegmentBound();
		IDistanceFunction distanceFunction = IDistanceFunction.create(segmentBound, holes);

		// (3) use EikMesh to improve the mesh
		double h0 = 1.0;
		var meshImprover = new PEikMesh(
				distanceFunction,
				edgeLengthFunctionApprox,
				h0,
				new VRectangle(segmentBound.getBounds2D()),
				pslg.getHoles()
		);

		var meshPanel = new PMeshPanel(meshImprover.getMesh(), 1000, 800);
		meshPanel.display("Combined distance functions " + h0);
		while (!meshImprover.isFinished()) {
			meshImprover.improve();
			Thread.sleep(20);
			meshPanel.repaint();
		}
		//meshImprover.generate();

		write(toTexDocument(TexGraphGenerator.toTikz(meshImprover.getMesh(), f-> lightBlue, 1.0f)), fileName+ Double.toString(h0).replace('.', '_'));

		// display the mesh
		meshPanel.display("Combined distance functions " + h0);
	}

	private static void write(final String string, final String filename) throws IOException {
		File outputFile = new File("./eikmesh/"+filename+".tex");
		try(FileWriter fileWriter = new FileWriter(outputFile)) {
			fileWriter.write(string);
		}
	}

	private static String toTexDocument(final String tikz) {
		return "\\documentclass[usenames,dvipsnames]{standalone}\n" +
				"\\usepackage[utf8]{inputenc}\n" +
				"\\usepackage{amsmath}\n" +
				"\\usepackage{amsfonts}\n" +
				"\\usepackage{amssymb}\n" +
				"\\usepackage{calc}\n" +
				"\\usepackage{graphicx}\n" +
				"\\usepackage{tikz}\n" +
				"\\usepackage{xcolor}\n" +
				"\n" +
				"%\\clip (-0.200000,-0.100000) rectangle (1.2,0.8);\n" +
				"\\begin{document}"+
				tikz
				+
				"\\end{document}";
	}
}