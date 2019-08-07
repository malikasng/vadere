package org.vadere.simulator.models.osm.opencl;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CLContextCallback;
import org.lwjgl.opencl.CLProgramCallback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.vadere.simulator.models.osm.PedestrianOSM;
import org.vadere.state.attributes.models.AttributesFloorField;
import org.vadere.state.attributes.models.AttributesOSM;
import org.vadere.util.geometry.GeometryUtils;
import org.vadere.util.geometry.shapes.VCircle;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;
import org.vadere.util.logging.Logger;
import org.vadere.util.opencl.CLInfo;
import org.vadere.util.opencl.CLUtils;
import org.vadere.util.opencl.OpenCLException;
import org.vadere.simulator.models.potential.solver.calculators.EikonalSolver;
import org.vadere.util.opencl.examples.InfoUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opencl.CL10.CL_CONTEXT_PLATFORM;
import static org.lwjgl.opencl.CL10.CL_DEVICE_LOCAL_MEM_SIZE;
import static org.lwjgl.opencl.CL10.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.lwjgl.opencl.CL10.CL_DEVICE_NAME;
import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.CL_MEM_ALLOC_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_PROFILING_COMMAND_END;
import static org.lwjgl.opencl.CL10.CL_PROFILING_COMMAND_START;
import static org.lwjgl.opencl.CL10.CL_PROGRAM_BUILD_LOG;
import static org.lwjgl.opencl.CL10.CL_PROGRAM_BUILD_STATUS;
import static org.lwjgl.opencl.CL10.CL_QUEUE_PROFILING_ENABLE;
import static org.lwjgl.opencl.CL10.CL_SUCCESS;
import static org.lwjgl.opencl.CL10.clBuildProgram;
import static org.lwjgl.opencl.CL10.clCreateBuffer;
import static org.lwjgl.opencl.CL10.clCreateCommandQueue;
import static org.lwjgl.opencl.CL10.clCreateContext;
import static org.lwjgl.opencl.CL10.clCreateKernel;
import static org.lwjgl.opencl.CL10.clCreateProgramWithSource;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clEnqueueReadBuffer;
import static org.lwjgl.opencl.CL10.clEnqueueWriteBuffer;
import static org.lwjgl.opencl.CL10.clFinish;
import static org.lwjgl.opencl.CL10.clGetDeviceIDs;
import static org.lwjgl.opencl.CL10.clGetEventProfilingInfo;
import static org.lwjgl.opencl.CL10.clGetKernelWorkGroupInfo;
import static org.lwjgl.opencl.CL10.clGetPlatformIDs;
import static org.lwjgl.opencl.CL10.clGetProgramBuildInfo;
import static org.lwjgl.opencl.CL10.clReleaseCommandQueue;
import static org.lwjgl.opencl.CL10.clReleaseContext;
import static org.lwjgl.opencl.CL10.clReleaseKernel;
import static org.lwjgl.opencl.CL10.clReleaseMemObject;
import static org.lwjgl.opencl.CL10.clReleaseProgram;
import static org.lwjgl.opencl.CL10.clSetKernelArg;
import static org.lwjgl.opencl.CL10.clSetKernelArg1f;
import static org.lwjgl.opencl.CL10.clSetKernelArg1i;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;
import static org.lwjgl.opencl.CL10.clWaitForEvents;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memUTF8;

/**
 * @author Benedikt Zoennchen
 *
 * This class offers the methods to compute an array based linked-cell which contains 2D-coordinates i.e. {@link VPoint}
 * using the GPU (see. green-2007 Building the Grid using Sorting).
 */
public class CLParallelEventDrivenOSM extends CLAbstractOSM implements ICLOptimalStepsModel {
	private static Logger log = Logger.getLogger(CLParallelEventDrivenOSM.class);

	static {
		log.setDebug();
	}

	private long clReorderedEventTimes;
	private long clEventTimesData;
	private long clIds;
	private long clMinEventTime;

	// Host Memory to write update to the host
	private FloatBuffer memEventTimes;

	// CL kernel
	private long clFindCellBoundsAndReorder;
	private long clEventTimes;
	private long clMove;
	private long clSwap;
	private long clSwapIndex;
	private long clCalcMinEventTime;

	private float[] eventTimes;
	private float minEventTime = 0;

	public CLParallelEventDrivenOSM(
			@NotNull final AttributesOSM attributesOSM,
			@NotNull final AttributesFloorField attributesFloorField,
			@NotNull final VRectangle bound,
			@NotNull final EikonalSolver targetPotential,
			@NotNull final EikonalSolver obstaclePotential,
			final double cellSize) throws OpenCLException {
		this(attributesOSM, attributesFloorField, bound, targetPotential, obstaclePotential, CL_DEVICE_TYPE_GPU, cellSize);
	}

	/**
	 * Default constructor.
	 *
	 * @param bound             the spatial bound of the linked cell.
	 *
	 * @throws OpenCLException
	 */
	public CLParallelEventDrivenOSM(
			@NotNull final AttributesOSM attributesOSM,
			@NotNull final AttributesFloorField attributesFloorField,
			@NotNull final VRectangle bound,
			@NotNull final EikonalSolver targetPotential,
			@NotNull final EikonalSolver obstaclePotential,
			final int device,
			final double cellSize) throws OpenCLException {
		super(attributesOSM, attributesFloorField, bound, targetPotential, obstaclePotential, device, cellSize);
		this.eventTimes = new float[0];
		super.COORDOFFSET = 2;
		super.X = 0;
		super.Y = 1;
		super.OFFSET = 2;
		super.STEPSIZE = 0;
		super.DESIREDSPEED = 1;
		init("ParallelEventDrivenOSM.cl");
	}

	/**
	 * Set's new set of agents which we want to simulate. This will remove all other agents.
	 * This method will free old data from the device memory and transfer required data to the device
	 * as well as reserve new device memory.
	 *
	 * @param pedestrians       the list of pedestrians / agents
	 * @throws OpenCLException
	 */
	@Override
	public void setPedestrians(@NotNull final List<PedestrianOSM> pedestrians) throws OpenCLException {

		// clear the old memory before re-initialization
		if(pedestrianSet) {
			freeCLMemory(clEventTimesData);
			freeCLMemory(clReorderedEventTimes);
			freeCLMemory(clIds);
			freeCLMemory(clMinEventTime);
			MemoryUtil.memFree(memEventTimes);
		}

		try (MemoryStack stack = stackPush()) {
			IntBuffer errcode_ret = stack.callocInt(1);
			clReorderedEventTimes = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4 * pedestrians.size(), errcode_ret);
			clIds = clCreateBuffer(clContext, CL_MEM_READ_WRITE, iGridSize[0] * iGridSize[1] * 4, errcode_ret);
			clEventTimesData = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4 * pedestrians.size(), errcode_ret);
			clMinEventTime = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4, errcode_ret);

			memEventTimes = allocPedestrianEventTimeMemory(pedestrians);
			clEnqueueWriteBuffer(clQueue, clEventTimesData, true, 0, memEventTimes, null, null);
		}

		super.setPedestrians(pedestrians);
	}


	//TODO: dont sort if the size is <= 1!
	@Override
	public boolean update(float timeStepInSec, float simTimeInSec) throws OpenCLException {
		try (MemoryStack stack = stackPush()) {
			allocGlobalHostMemory();
			allocGlobalDeviceMemory();
			long clGlobalIndexOut = !swap ? this.clGlobalIndexOut : this.clGlobalIndexIn;
			long clGlobalIndexIn = !swap ? this.clGlobalIndexIn : this.clGlobalIndexOut;

			clCalcHash(clHashes, clIndices, clPositions, clCellSize, clWorldOrigin, clGridSize, numberOfElements, numberOfSortElements);
			clBitonicSort(clHashes, clIndices, clHashes, clIndices, numberOfSortElements, 1);

			clFindCellBoundsAndReorder(
					clCellStarts,
					clCellEnds,
					clReorderedPedestrians,
					clReorderedPositions,
					clReorderedEventTimes,
					clHashes, clIndices, clPedestrians, clPositions, clEventTimesData, numberOfElements);

			clEventTimes(
					clIds,
					clReorderedEventTimes,
					clCellStarts,
					clCellEnds);

			clMove(
					clReorderedPedestrians,
					clReorderedPositions,
					clReorderedEventTimes,
					clIds,
					clCirclePositions,
					clCellStarts,
					clCellEnds,
					clCellSize,
					clGridSize,
					clObstaclePotential,
					clTargetPotential,
					clWorldOrigin,
					clPotentialFieldGridSize,
					clPotentialFieldSize,
					numberOfElements,
					simTimeInSec);

			clSwap(
					clReorderedPedestrians,
					clReorderedPositions,
					clReorderedEventTimes,
					clPedestrians,
					clPositions,
					clEventTimesData,
					numberOfElements);

			clSwapIndex(
					clGlobalIndexOut,
					clGlobalIndexIn,
					clIndices,
					numberOfElements);

			clMinEventTime(clMinEventTime, clEventTimesData, numberOfElements);

			clFinish(clQueue);

			FloatBuffer memMinEventTime = stack.callocFloat(1);

			clEnqueueReadBuffer(clQueue, clMinEventTime, true, 0, memMinEventTime, null, null);
			clFinish(clQueue);

			clMemSet(clCellStarts, -1, iGridSize[0] * iGridSize[1]);
			clMemSet(clCellEnds, -1, iGridSize[0] * iGridSize[1]);

			counter++;
			swap = !swap;
			minEventTime = memMinEventTime.get(0);

			return minEventTime < simTimeInSec;
		}
	}

	@Override
	public void readFromDevice() {
		super.readFromDevice();
		clEnqueueReadBuffer(clQueue, clEventTimesData, true, 0, memEventTimes, null, null);
		clFinish(clQueue);

		eventTimes = new float[numberOfElements];
		float[] tmp = CLUtils.toFloatArray(memEventTimes, numberOfElements);
		for(int i = 0; i < numberOfElements; i++) {
			eventTimes[indices[i]] = tmp[i];
		}
	}

	public float getMinEventTime() {
		return minEventTime;
	}

	public int getCounter() {
		return counter;
	}

	@Override
	public List<VPoint> getPositions() {
		return positions;
	}

	public float[] getEventTimes() {
		/*System.out.println("event times");
		for(float et : eventTimes) {
			System.out.println(et);
		}*/
		return eventTimes;
	}

	/**
	 * Transforms the a list of {@link PedestrianOSM} into a {@link FloatBuffer} i.e. a array
	 * @param pedestrians
	 * @return
	 */
	@Override
	protected FloatBuffer allocPedestrianHostMemory(@NotNull final List<PedestrianOSM> pedestrians) {
		float[] pedestrianStruct = new float[pedestrians.size() * OFFSET];
		for(int i = 0; i < pedestrians.size(); i++) {
			pedestrianStruct[i * OFFSET + STEPSIZE] = (float) pedestrians.get(i).getDesiredStepSize();
			pedestrianStruct[i * OFFSET + DESIREDSPEED] = (float) pedestrians.get(i).getDesiredSpeed();
		}
		return CLUtils.toFloatBuffer(pedestrianStruct);
	}

	private FloatBuffer allocPedestrianEventTimeMemory(@NotNull final List<PedestrianOSM> pedestrians) {
		float[] pedestrianStruct = new float[pedestrians.size()];
		for(int i = 0; i < pedestrians.size(); i++) {
			pedestrianStruct[i] = 0.0f;
		}
		return CLUtils.toFloatBuffer(pedestrianStruct);
	}

	@Override
	protected FloatBuffer allocPositionHostMemory(@NotNull final List<PedestrianOSM> pedestrians) {
		float[] pedestrianStruct = new float[pedestrians.size() * COORDOFFSET];
		for(int i = 0; i < pedestrians.size(); i++) {
			pedestrianStruct[i * COORDOFFSET + X] = (float) pedestrians.get(i).getPosition().getX();
			pedestrianStruct[i * COORDOFFSET + Y] = (float) pedestrians.get(i).getPosition().getY();
		}
		return CLUtils.toFloatBuffer(pedestrianStruct);
	}

	/**
	 * Allocates the host memory for objects which do not change during the simulation e.g. the static potential field.
	 * Therefore this initialization is done once for a simulation.
	 */
	@Override
	protected void allocGlobalHostMemory() {
		super.allocGlobalHostMemory();
	}

	/**
	 * Allocates the device memory for objects which do not change during the simulation e.g. the static potential field.
	 * Therefore this initialization is done once for a simulation.
	 */
	protected void allocGlobalDeviceMemory() {
		super.allocGlobalDeviceMemory();
	}

	private void clMove(
			final long clReorderedPedestrians,
			final long clReorderedPositions,
			final long clReorderedEventTimes,
			final long clIds,
			final long clCirclePositions,
			final long clCellStarts,
			final long clCellEnds,
			final long clCellSize,
			final long clGridSize,
			final long clObstaclePotential,
			final long clTargetPotential,
			final long clWorldOrigin,
			final long clPotentialFieldGridSize,
			final long clPotentialFieldSize,
			final int numberOfElements,
			final float simTimeInSec)
			throws OpenCLException {
		try (MemoryStack stack = stackPush()) {

			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			//long maxWorkGroupSize = CLUtils.getMaxWorkGroupSizeForKernel(clDevice, clMove, 0, max_work_group_size, max_local_memory_size); // local 4 byte (integer)

			CLInfo.checkCLError(clSetKernelArg1p(clMove, 0, clReorderedPedestrians));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 1, clReorderedPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 2, clReorderedEventTimes));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 3, clIds));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 4, clCirclePositions));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 5, clCellStarts));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 6, clCellEnds));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 7, clCellSize));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 8, clGridSize));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 9, clObstaclePotential));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 10, clTargetPotential));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 11, clWorldOrigin));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 12, clPotentialFieldGridSize));
			CLInfo.checkCLError(clSetKernelArg1p(clMove, 13, clPotentialFieldSize));
			CLInfo.checkCLError(clSetKernelArg1f(clMove, 14, (float)attributesFloorField.getPotentialFieldResolution()));
			CLInfo.checkCLError(clSetKernelArg1i(clMove, 15, circlePositionList.size()));
			CLInfo.checkCLError(clSetKernelArg1i(clMove, 16, numberOfElements));
			CLInfo.checkCLError(clSetKernelArg1f(clMove, 17, simTimeInSec));

			long globalWorkSize;
			globalWorkSize = iGridSize[0] * iGridSize[1];
			clGlobalWorkSize.put(0, globalWorkSize);

			//TODO: local work size? + check 2^n constrain!
			CLInfo.checkCLError((int)enqueueNDRangeKernel("clMove", clQueue, clMove, 1, null, clGlobalWorkSize, null, null, null));
		}
	}

	private void clSwap(
			final long clReorderedPedestrians,
			final long clReorderedPositions,
			final long clReorderedEventTimes,
			final long clPedestrians,
			final long clPositions,
			final long clEventTimes,
			final int numberOfElements) throws OpenCLException {

		try (MemoryStack stack = stackPush()) {
			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			clGlobalWorkSize.put(0, numberOfElements);

			CLInfo.checkCLError(clSetKernelArg1p(clSwap, 0, clReorderedPedestrians));
			CLInfo.checkCLError(clSetKernelArg1p(clSwap, 1, clReorderedPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clSwap, 2, clReorderedEventTimes));
			CLInfo.checkCLError(clSetKernelArg1p(clSwap, 3, clPedestrians));
			CLInfo.checkCLError(clSetKernelArg1p(clSwap, 4, clPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clSwap, 5, clEventTimes));
			CLInfo.checkCLError(clSetKernelArg1i(clSwap, 6, numberOfElements));
			CLInfo.checkCLError((int)enqueueNDRangeKernel("clSwap", clQueue, clSwap, 1, null, clGlobalWorkSize, null, null, null));
		}
	}

	/*
	__kernel void minEventTimeLocal(
    __global float* minEventTime,
    __global float* eventTimes,
    __local  float* local_eventTimes,
    uint numberOfElements
	 */

	private void clMinEventTime(
			final long clMinEventTime,
			final long clEventTimes,
			final int numberOfElements) throws OpenCLException {

		try (MemoryStack stack = stackPush()) {
			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			PointerBuffer clLocalWorkSize = stack.callocPointer(1);
			IntBuffer errcode_ret = stack.callocInt(1);
			long maxWorkGroupSize = CLUtils.getMaxWorkGroupSizeForKernel(clDevice, clFindCellBoundsAndReorder, 4, getMaxWorkGroupSize(), getMaxLocalMemorySize()); // local 4 byte (integer)
			clGlobalWorkSize.put(0, Math.min(maxWorkGroupSize, numberOfElements));
			clLocalWorkSize.put(0, Math.min(maxWorkGroupSize, numberOfElements));

			CLInfo.checkCLError(clSetKernelArg1p(clCalcMinEventTime, 0, clMinEventTime));
			CLInfo.checkCLError(clSetKernelArg1p(clCalcMinEventTime, 1, clEventTimes));
			CLInfo.checkCLError(clSetKernelArg(clCalcMinEventTime, 2, maxWorkGroupSize * 4));
			CLInfo.checkCLError(clSetKernelArg1i(clCalcMinEventTime, 3, numberOfElements));
			CLInfo.checkCLError((int)enqueueNDRangeKernel("clCalcMinEventTime", clQueue, clCalcMinEventTime, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
		}
	}

	private void clEventTimes(
			final long clIds,
			final long clReorderedEventTimes,
			final long clCellStarts,
			final long clCellEnds) throws OpenCLException {

		try (MemoryStack stack = stackPush()) {
			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			clGlobalWorkSize.put(0, iGridSize[0] * iGridSize[1]);

			CLInfo.checkCLError(clSetKernelArg1p(clEventTimes, 0, clIds));
			CLInfo.checkCLError(clSetKernelArg1p(clEventTimes, 1, clReorderedEventTimes));
			CLInfo.checkCLError(clSetKernelArg1p(clEventTimes, 2, clCellStarts));
			CLInfo.checkCLError(clSetKernelArg1p(clEventTimes, 3, clCellEnds));
			CLInfo.checkCLError((int)enqueueNDRangeKernel("clEventTimes", clQueue, clEventTimes, 1, null, clGlobalWorkSize, null, null, null));
		}
	}

	private void clFindCellBoundsAndReorder(
			final long clCellStarts,
			final long clCellEnds,
			final long clReorderedPedestrians,
			final long clReorderedPositions,
			final long clReorderedEventTimes,
			final long clHashes,
			final long clIndices,
			final long clPedestrians,
			final long clPositions,
			final long clEventTimes,
			final int numberOfElements) throws OpenCLException {

		try (MemoryStack stack = stackPush()) {

			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			PointerBuffer clLocalWorkSize = stack.callocPointer(1);
			IntBuffer errcode_ret = stack.callocInt(1);
			long maxWorkGroupSize = CLUtils.getMaxWorkGroupSizeForKernel(clDevice, clFindCellBoundsAndReorder, 0, getMaxWorkGroupSize(), getMaxLocalMemorySize()); // local 4 byte (integer)

			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 0, clCellStarts));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 1, clCellEnds));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 2, clReorderedPedestrians));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 3, clReorderedPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 4, clReorderedEventTimes));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 5, clHashes));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 6, clIndices));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 7, clPedestrians));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 8, clPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 9, clEventTimes));
			CLInfo.checkCLError(clSetKernelArg(clFindCellBoundsAndReorder, 10, (Math.min(numberOfElements+1, maxWorkGroupSize)) * 4)); // local memory
			CLInfo.checkCLError(clSetKernelArg1i(clFindCellBoundsAndReorder, 11, numberOfElements));

			long globalWorkSize;
			long localWorkSize;
			if(numberOfElements+1 < maxWorkGroupSize){
				localWorkSize = numberOfElements;
				globalWorkSize = numberOfElements;
			}
			else {
				localWorkSize = maxWorkGroupSize;
				globalWorkSize = CLUtils.multiple(numberOfElements, localWorkSize);
			}

			clGlobalWorkSize.put(0, globalWorkSize);
			clLocalWorkSize.put(0, localWorkSize);
			//TODO: local work size? + check 2^n constrain!
			CLInfo.checkCLError((int)enqueueNDRangeKernel("clFindCellBoundsAndReorder", clQueue, clFindCellBoundsAndReorder, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
		}
	}

	protected void clearMemory() throws OpenCLException {
		super.clearMemory();
		// release memory and devices
		try {
			if(pedestrianSet) {
				CLInfo.checkCLError(clReleaseMemObject(clEventTimesData));
				CLInfo.checkCLError(clReleaseMemObject(clIds));
				CLInfo.checkCLError(clReleaseMemObject(clReorderedEventTimes));
				CLInfo.checkCLError(clReleaseMemObject(clMinEventTime));

			}
		}
		catch (OpenCLException ex) {
			throw ex;
		}
		finally {
			if(pedestrianSet) {
				MemoryUtil.memFree(memEventTimes);
			}
		}
	}

	@Override
	protected void releaseKernels() throws OpenCLException {
		super.releaseKernels();
		CLInfo.checkCLError(clReleaseKernel(clFindCellBoundsAndReorder));
		CLInfo.checkCLError(clReleaseKernel(clEventTimes));
		CLInfo.checkCLError(clReleaseKernel(clMove));
		CLInfo.checkCLError(clReleaseKernel(clSwap));
		CLInfo.checkCLError(clReleaseKernel(clSwapIndex));
		CLInfo.checkCLError(clReleaseKernel(clCalcMinEventTime));
	}

	@Override
	protected void buildKernels() throws OpenCLException {
		super.buildKernels();
		try (MemoryStack stack = stackPush()) {
			IntBuffer errcode_ret = stack.callocInt(1);

			clFindCellBoundsAndReorder = clCreateKernel(clProgram, "findCellBoundsAndReorder", errcode_ret);
			CLInfo.checkCLError(errcode_ret);

			clEventTimes = clCreateKernel(clProgram, "eventTimes", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clMove = clCreateKernel(clProgram, "move", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clSwap = clCreateKernel(clProgram, "swap", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clSwapIndex = clCreateKernel(clProgram, "swapIndex", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clCalcMinEventTime = clCreateKernel(clProgram, "minEventTimeLocal", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
		}
	}
}