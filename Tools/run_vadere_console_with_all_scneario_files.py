# Use "vadere-console.jar", which is created by "mvn package", to run all
# scenario files under "VadereModelTests" subdirectory.
#
# Note: script contains some print statements so that progress can be tracked
# a little bit

# Wach out: call this script from root directory of project. E.g.
#
#   python Tools/my_script.py

import fnmatch
import os
import shutil
import subprocess

def find_scenario_files(path="VadereModelTests"):
    scenario_search_pattern = "*.scenario"
    scenario_files = []

    for root, dirnames, filenames in os.walk(path):
        for filename in fnmatch.filter(filenames, scenario_search_pattern):
            scenario_path = os.path.join(root, filename)
            scenario_files.append(scenario_path)

    print("Total scenario files: {}".format(len(scenario_files)))

    return scenario_files

def run_scenario_files_with_vadere_console(scenario_files, vadere_console="VadereGui/target/vadere-console.jar", scenario_timeout_in_sec=60):
    output_dir = "output"

    if not os.path.exists(output_dir):
        print("Creating output directory: {}".format(output_dir))
        os.makedirs(output_dir)
        print("Created output directory: {}".format(output_dir))

    for scenario_file in scenario_files:
        print("Running scenario file: {}".format(scenario_file))
        # Use timout feature, check return value and capture stdout/stderr to a PIPE (use completed_process.stdout to get it).
        completed_process = subprocess.run(args=["java", "-jar", vadere_console, scenario_file, output_dir],
                                       timeout=scenario_timeout_in_sec,
                                       check=True,
                                       stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE)
        print("Finished scenario file: {}".format(scenario_file))

    if os.path.exists(output_dir):
        print("Deleting output directory: {}".format(output_dir))
        shutil.rmtree(output_dir)
        print("Deleted output directory: {}".format(output_dir))

if __name__ == "__main__":
    scenario_files = find_scenario_files()
    run_scenario_files_with_vadere_console(scenario_files)
