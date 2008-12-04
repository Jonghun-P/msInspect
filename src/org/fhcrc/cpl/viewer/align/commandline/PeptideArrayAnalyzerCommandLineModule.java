/* 
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fhcrc.cpl.viewer.align.commandline;

import org.fhcrc.cpl.viewer.commandline.modules.BaseViewerCommandLineModuleImpl;
import org.fhcrc.cpl.toolbox.commandline.arguments.ArgumentValidationException;
import org.fhcrc.cpl.toolbox.commandline.arguments.CommandLineArgumentDefinition;
import org.fhcrc.cpl.toolbox.commandline.arguments.EnumeratedValuesArgumentDefinition;
import org.fhcrc.cpl.toolbox.commandline.arguments.ArgumentDefinitionFactory;
import org.fhcrc.cpl.viewer.align.PeptideArrayAnalyzer;
import org.fhcrc.cpl.toolbox.gui.chart.ScatterPlotDialog;
import org.fhcrc.cpl.viewer.feature.Feature;
import org.fhcrc.cpl.viewer.feature.FeatureSet;
import org.fhcrc.cpl.viewer.feature.extraInfo.MS2ExtraInfoDef;
import org.fhcrc.cpl.toolbox.ApplicationContext;
import org.fhcrc.cpl.toolbox.TabLoader;
import org.fhcrc.cpl.toolbox.Pair;
import org.fhcrc.cpl.toolbox.commandline.CommandLineModuleExecutionException;
import org.fhcrc.cpl.toolbox.commandline.CommandLineModule;
import org.apache.log4j.Logger;

import java.util.*;
import java.io.*;


/**
 * Command linemodule for feature finding
 */
public class PeptideArrayAnalyzerCommandLineModule extends BaseViewerCommandLineModuleImpl
        implements CommandLineModule
{
    protected static Logger _log = Logger.getLogger(PeptideArrayAnalyzerCommandLineModule.class);

    protected File file;
    protected File outFile;
    protected File outDir;
    protected File detailsFile;
    boolean allowHalfMatched=false;
    String[] caseRunNames;
    String[] controlRunNames;
    protected double minSignificantRatio = 3;

    protected int minRunsForConsensusFeature = 2;

    PeptideArrayAnalyzer peptideArrayAnalyzer = null;

    protected boolean showCharts = false;

    Object[] arrayRows;

    protected int minPeptideSupport = 1;
    protected int minFeatureSupport = 1;

    protected static final int MODE_GET_INFO = 0;
    protected static final int MODE_CREATE_FEATURE_FILES_ALL_MATCHED = 1;
    protected static final int MODE_COUNT_MULTIPLY_OBSERVED_PEPTIDEES = 2;
    protected static final int MODE_COMPARE_INTENSITIES_SAME_PEPTIDE = 3;
    protected static final int MODE_CREATE_CONSENSUS_FEATURE_FILE = 4;
    protected static final int MODE_COMPARE_ALL_INTENSITIES = 5;
    protected static final int MODE_COMPARE_ALL_INTENSITIES_ADD_1 = 6;
    protected static final int MODE_COMPARE_INTENSITIES_SAME_PEPTIDE_ADD_1 = 7;
    protected static final int MODE_COMPARE_NON_PEPTIDE_INTENSITIES = 8;


    protected final static String[] modeStrings =
            {
                    "getinfo",
                    "createfeaturefilesallmatched",
                    "countmultiplyobservedpeptides",
                    "comparepeptideintensities",
                    "createconsensusfeaturefile",
                    "compareallintensities",
                    "compareallintensitiesadd1",
                    "comparepeptideintensitiesadd1",
                    "comparenonpeptideintensities"
            };

    public static final String[] modeExplanations =
            {
                    "Get basic peptide array information",
                    "Create feature files, one per run, containing only those features matched across all runs",
                    "Count the distinct peptides observed in multiple runs",
                    "Compare peptide intensities matched across runs",
                    "Create a 'consensus' feature file, containing features (with details from the first run) that align across at least minconsensusfeatureruns runs",
                    "Compare intensity in the case runs vs. intensity in the control runs",
                    "Compare intensity in the case runs vs. intensity in the control runs, adding 1, so that logs can be used",
                    "Compare intensity in the case runs vs. the control runs for features mapped to the same peptide",
                    "Compare intensity in the case runs vs. the control runs for features mapped to the same peptide, adding 1, so that logs can be used",
                    "Compare intensity in the case runs vs. the control runs for features that do NOT have a peptide assignment"
            };

    protected int mode=-1;

    public PeptideArrayAnalyzerCommandLineModule()
    {
        init();
    }

    protected void init()
    {
        mCommandName = "analyzepeparray";

        mShortDescription = "Tools for analyzing peptide arrays.";
        mHelpMessage = "Tools for analyzing peptide arrays, for comparing MS2 results in one set of runs to "
                + "another and for summarizing overlap of MS1 features found in different runs.";

        CommandLineArgumentDefinition[] argDefs =
               {
                    createEnumeratedArgumentDefinition("mode",true,modeStrings, modeExplanations),
                    createUnnamedArgumentDefinition(ArgumentDefinitionFactory.FILE_TO_READ,true, null),
                    createFileToWriteArgumentDefinition("out",false, "output file"),
                    createFileToWriteArgumentDefinition("outdir",false, "output directory"),

                    createBooleanArgumentDefinition("allowhalfmatched",false,"When determining whether peptide matches are made, should it be considered a match when one run has an ID and another run has an intensity but no ID?",
                            allowHalfMatched),
                    createFileToReadArgumentDefinition("caserunlistfile",false,"File containing the names of runs in the case group, one per line"),
                    createFileToReadArgumentDefinition("controlrunlistfile",false,"File containing the names of runs in the control group, one per line"),

                    createDecimalArgumentDefinition("minsignificantratio",false,"Minimum ratio of intensities considered interesting",
                            minSignificantRatio),
                    createIntegerArgumentDefinition("minconsensusfeatureruns",false,
                            "Minimum number of runs required for a feature to be included in the consensus feature set",
                            minRunsForConsensusFeature),
                    createIntegerArgumentDefinition("minpeptidesupport",false,
                            "Minimum number of runs for which the same peptide was identified",
                            minPeptideSupport),
                    createIntegerArgumentDefinition("minfeaturesupport",false,
                            "Minimum number of runs for which a non-peptide-conflicting feature was identified",
                            minFeatureSupport),
                       createBooleanArgumentDefinition("showcharts", false, "show charts?", showCharts),
               };
        addArgumentDefinitions(argDefs);
    }

    public void assignArgumentValues()
            throws ArgumentValidationException
    {
        mode = ((EnumeratedValuesArgumentDefinition) getArgumentDefinition("mode")).getIndexForArgumentValue(getStringArgumentValue("mode"));

        file = getFileArgumentValue(CommandLineArgumentDefinition.UNNAMED_PARAMETER_VALUE_ARGUMENT);
        outFile = getFileArgumentValue("out");
        outDir = getFileArgumentValue("outdir");

        showCharts = getBooleanArgumentValue("showcharts");

        try
        {
            peptideArrayAnalyzer = new PeptideArrayAnalyzer(file);
        }
        catch (Exception e)
        {
            throw new ArgumentValidationException(e);
        }

        allowHalfMatched = getBooleanArgumentValue("allowhalfmatched");
        File caseRunListFile = getFileArgumentValue("caserunlistfile");
        File controlRunListFile = getFileArgumentValue("controlrunlistfile");

        if (caseRunListFile != null)
        {
            try
            {
                BufferedReader br = new BufferedReader(new FileReader(caseRunListFile));
                List<String> caseRunNameList = new ArrayList<String>();
                while (true)
                {
                    String line = br.readLine();
                    if (null == line)
                        break;
                    if (line.length() == 0 || line.charAt(0) == '#')
                        continue;
                    caseRunNameList.add(line);
                }
                caseRunNames = caseRunNameList.toArray(new String[0]);

                if (controlRunListFile != null)
                {
                    br = new BufferedReader(new FileReader(controlRunListFile));
                    List<String> controlRunNameList = new ArrayList<String>();
                    while (true)
                    {
                        String line = br.readLine();
                        if (null == line)
                            break;
                        if (line.length() == 0 || line.charAt(0) == '#')
                            continue;
                        controlRunNameList.add(line);
                    }
                    controlRunNames = controlRunNameList.toArray(new String[0]);
                }
                else
                {
                    List<String> controlRunNameList = new ArrayList<String>();
                    for (String runName : peptideArrayAnalyzer.getRunNames())
                        if (!caseRunNameList.contains(runName))
                            controlRunNameList.add(runName);
                    controlRunNames = controlRunNameList.toArray(new String[controlRunNameList.size()]);
                }

                ApplicationContext.infoMessage("Case runs:");
                for (String caseRun : caseRunNames)
                    ApplicationContext.infoMessage("\t" + caseRun);
                ApplicationContext.infoMessage("Control runs:");
                for (String controlRun : controlRunNames)
                    ApplicationContext.infoMessage("\t" + controlRun);
            }
            catch (Exception e)
            {
                throw new ArgumentValidationException(e);
            }
        }
        else
        {
            List<String> runNames = peptideArrayAnalyzer.getRunNames();
            if (runNames.size() == 2)
            {
                ApplicationContext.setMessage("No case/control run names specified.  Assuming run 1 is control, run 2 is case");
                caseRunNames = new String[1];
                caseRunNames[0] = runNames.get(0);                
                controlRunNames = new String[1];
                controlRunNames[0] = runNames.get(1);
                ApplicationContext.setMessage("Control run: " + controlRunNames[0] + ", Case run: " + caseRunNames[0]);
            }
        }

        peptideArrayAnalyzer.setCaseRunNames(caseRunNames);
        peptideArrayAnalyzer.setControlRunNames(controlRunNames);

        minSignificantRatio = getDoubleArgumentValue("minsignificantratio");

        minRunsForConsensusFeature = getIntegerArgumentValue("minconsensusfeatureruns");

        minPeptideSupport = getIntegerArgumentValue("minpeptidesupport");
        minFeatureSupport = getIntegerArgumentValue("minfeaturesupport");

        try
        {
            switch (mode)
            {
                case MODE_CREATE_FEATURE_FILES_ALL_MATCHED:
                    assertArgumentPresent("outdir");
                    break;
                case MODE_CREATE_CONSENSUS_FEATURE_FILE:
                    assertArgumentPresent("out");
                    assertArgumentAbsent("outdir");

                    String arrayFilePrefix =
                            file.getName().substring(0, file.getName().indexOf("."));
                    for (String fileName : file.getAbsoluteFile().getParentFile().list())
                    {
                        if (fileName.contains(arrayFilePrefix) &&
                            fileName.endsWith(".details.tsv"))
                        {
                            detailsFile = new File(file.getParentFile(), fileName);                 
                            break;
                        }
                    }
                    if (detailsFile == null)
                        throw new ArgumentValidationException("Unable to find details file for array " +
                                file.getName());
                    break;
                case MODE_GET_INFO:
                    if (caseRunNames != null)
                    {
                        List<String> controlRunNameList =
                                new ArrayList<String>();
                        for (String runName : peptideArrayAnalyzer.getRunNames())
                        {
                            boolean isCase= false;
                            for (String caseRunName : caseRunNames)
                            {
                                if (caseRunName.equals(runName))
                                {
                                    isCase = true;
                                    break;
                                }
                            }
                            if (!isCase)
                                controlRunNameList.add(runName);
                        }
                        controlRunNames = controlRunNameList.toArray(new String[0]);
                    }
                    else
                    {
                        if (peptideArrayAnalyzer.getRunNames().size() == 2)
                        {
                            controlRunNames = new String[] { peptideArrayAnalyzer.getRunNames().get(0) };
                            caseRunNames = new String[] { peptideArrayAnalyzer.getRunNames().get(1) };
                        }
                    }
                    if (caseRunNames != null)
                    {
                        System.err.println("Case runs:");
                        for (String caseRunName : caseRunNames)
                            System.err.println("   " + caseRunName);
                        System.err.println("Control runs:");
                        for (String controlRunName : controlRunNames)
                            System.err.println("   " + controlRunName);
                        break;
                    }
            }
        }
        catch (Exception e)
        {
            throw new ArgumentValidationException(e);
        }
    }


    /**
     * do the actual work
     */
    public void execute() throws CommandLineModuleExecutionException
    {
        try
        {
            TabLoader tabLoader = new TabLoader(file);
            tabLoader.setReturnElementClass(HashMap.class);
            Object[] rows = tabLoader.load();
            ApplicationContext.setMessage("Array rows: " + rows.length);
            List<String> runNames = new ArrayList<String>();

            for (TabLoader.ColumnDescriptor  column : tabLoader.getColumns())
            {
                _log.debug("loading column " + column.name);
                if (column.name.startsWith("intensity_"))
                {
                    runNames.add(column.name.substring("intensity_".length()));
                    _log.debug("adding run " + runNames.get(runNames.size()-1));
                }
            }
            switch (mode)
            {
                case MODE_GET_INFO:
                    getInfo();
                    break;
                case MODE_CREATE_FEATURE_FILES_ALL_MATCHED:
                    peptideArrayAnalyzer.createFeatureFilesAllMatched(outDir);
                    break;
                case MODE_CREATE_CONSENSUS_FEATURE_FILE:
                    FeatureSet consensusFeatureSet =
                        peptideArrayAnalyzer.createConsensusFeatureSet( detailsFile,
                            minRunsForConsensusFeature,
                                PeptideArrayAnalyzer.CONSENSUS_INTENSITY_MODE_MEAN);
                    ApplicationContext.infoMessage("Created consensus feature set with " +
                        consensusFeatureSet.getFeatures().length + " features");
                    consensusFeatureSet.save(outFile);
                    ApplicationContext.infoMessage("Saved consensus feature set to file " +
                        outFile.getAbsolutePath());
                    break;
                case MODE_COUNT_MULTIPLY_OBSERVED_PEPTIDEES:
                    Map<String, Set<String>> runMatchedPeptides =
                            peptideArrayAnalyzer.getRunMatchedPeptides();

                    Set<String> allMatchedPeptides = new HashSet<String>();
                    Map<String, Integer> peptideMatchCountMap =
                            new HashMap<String, Integer>();
                    //This is horribly inefficient, but correct
                    for (Set<String> runPeptides : runMatchedPeptides.values())
                    {
                        for (String peptideInRun : runPeptides)
                        {
                            if (peptideMatchCountMap.keySet().contains(peptideInRun))
                            {
                                peptideMatchCountMap.put(peptideInRun,
                                        peptideMatchCountMap.get(peptideInRun) + 1);
                            }
                            else
                                peptideMatchCountMap.put(peptideInRun,1);
                        }
                    }
                    Set<String> multiplyMatchedPeptides = new HashSet<String>();
                    Set<String> peptidesMatchedAtLeastThrice = new HashSet<String>();

                    for (String peptide : peptideMatchCountMap.keySet())
                    {
                        allMatchedPeptides.add(peptide);
                        int count = peptideMatchCountMap.get(peptide);
                        if (count>2)
                            multiplyMatchedPeptides.add(peptide);
                        if (count>3)
                            peptidesMatchedAtLeastThrice.add(peptide);
                    }
                    System.err.println("Multiply matched peptides: " + multiplyMatchedPeptides.size());
                    System.err.println("Peptides matched at least 3 times: " + peptidesMatchedAtLeastThrice.size());

                    System.err.println("(out of " + allMatchedPeptides.size() + " total matched)");
                    break;
                case MODE_COMPARE_INTENSITIES_SAME_PEPTIDE:
                    compareIntensitiesSamePeptide(rows, caseRunNames, controlRunNames, false);
                    break;

/*
String[] pmol5 = new String[] {"peptide_pool10_normal_5pmol"};
String[] pmol4 = new String[] {"peptide_pool10_normal_4pmol"};
String[] pmol3 = new String[] {"peptide_pool10_normal_3pmol"};  



ScatterPlotDialog spd = new ScatterPlotDialog();
Pair<double[], double[]> xValsYVals =compareIntensitiesSamePeptide(rows, pmol5, pmol4,false);
System.err.println("5:4:   " + xValsYVals.first.length);



spd.addData(xValsYVals.first, xValsYVals.second, "5:4 plot");

Pair<double[], double[]>  xValsYVals2=compareIntensitiesSamePeptide(rows, pmol5, pmol3,false);
spd.addData(xValsYVals2.first, xValsYVals2.second, "5:3 plot");

System.err.println("5:3:   " + xValsYVals2.first.length);


double[] lineX = new double[5000];
double[] lineY = new double[5000];
for (int c=1; c<lineX.length; c++)
{
    lineX[c] = c;// Math.log(c);
    lineY[c] =4.0 * c / 5.0;// Math.log(4.0 * c / 5.0);
}
spd.addData(lineX, lineY,"4:5 line");
//this is special-purpose
double[] line35X = new double[5000];
double[] line35Y = new double[5000];
for (int c=1; c<lineX.length; c++)
{
    lineX[c] = c;//Math.log(c);
    lineY[c] = 3.0 * c / 5.0;//Math.log(3.0 * c / 5.0);
}
spd.addData(lineX, lineY,"3:5 line");
spd.setVisible(true);

double[] martyXVals = new double[xValsYVals.second.length];
double[] martyYVals = new double[xValsYVals.second.length];
double[] martyXVals2 = new double[xValsYVals2.second.length];
double[] martyYVals2 = new double[xValsYVals2.second.length];

ScatterPlotDialog spd2 = new ScatterPlotDialog();
//spd2.getPanelWithScatterPlot().getChart().getXYPlot().setRangeAxis(new LogarithmicAxis("asdf"));

for (int i=0; i<martyXVals.length; i++)
{
    martyXVals[i] = Math.log(xValsYVals.first[i]) + Math.log(xValsYVals.second[i]);
    martyYVals[i] = Math.log(xValsYVals.first[i]) - Math.log(xValsYVals.second[i]);
}
for (int i=0; i<martyXVals2.length; i++)
{
    martyXVals2[i] = Math.log(xValsYVals2.first[i]) + Math.log(xValsYVals2.second[i]);
    martyYVals2[i] = Math.log(xValsYVals2.first[i]) - Math.log(xValsYVals2.second[i]);
}
spd2.addData(martyXVals, martyYVals, "5:4 plot");
spd2.addData(martyXVals2, martyYVals2, "5:3 plot");

double[] linelog45X = new double[5000];
double[] linelog45Y = new double[5000];
for (int c=1; c<linelog45X.length; c++)
{
    linelog45X[c] = 16.0 * c / 5000;
    linelog45Y[c] = Math.log(5) - Math.log(4);
}
spd2.addData(linelog45X, linelog45Y,"4:5 line");
//this is special-purpose
double[] linelog35X = new double[5000];
double[] linelog35Y = new double[5000];
for (int c=1; c<linelog35X.length; c++)
{
    linelog35X[c] = 16.0 * c / 5000;
    linelog35Y[c] = Math.log(5) - Math.log(3);
}
spd2.addData(linelog35X, linelog35Y,"3:5 line");
spd2.setVisible(true);


break;
*/


                case MODE_COMPARE_INTENSITIES_SAME_PEPTIDE_ADD_1:
                    compareIntensitiesSamePeptide(rows, caseRunNames, controlRunNames, true);
                    break;                    
                case MODE_COMPARE_ALL_INTENSITIES:
                    peptideArrayAnalyzer.compareIntensities(false);
                    break;
                case MODE_COMPARE_ALL_INTENSITIES_ADD_1:
                    peptideArrayAnalyzer.compareIntensities(true);
                    break;
                case MODE_COMPARE_NON_PEPTIDE_INTENSITIES:
                    compareNonPeptideIntensities();
                    break;

            }
        }
        catch (Exception e)
        {
            throw new CommandLineModuleExecutionException(e);
        }
    }

    protected void getInfo()
    {

        peptideArrayAnalyzer.analyzeMs2();
        System.err.println("Conflict rows: " + peptideArrayAnalyzer.countConflictRows());
        peptideArrayAnalyzer.analyzeMs1();
        if (caseRunNames != null)
            peptideArrayAnalyzer.compareIntensitiesSamePeptide(minSignificantRatio);

    }


    /**
     * Compare mean intensities of certain columns against each other in rows
     * in which the same peptide is identified in enough runs.
     *
     * A lot of this is hardcoded for a particular purpose right now.
     * @param rows
     * @throws CommandLineModuleExecutionException
     */

    protected Pair<double[], double[]> compareIntensitiesSamePeptide(Object[] rows,
                                                 String[] caseRunNames,
                                                 String[] controlRunNames,
                                                 boolean add1)
            throws CommandLineModuleExecutionException
    {
        if (caseRunNames == null || controlRunNames == null)
            throw new CommandLineModuleExecutionException("Error: You must define case and control runs");

        Set<String> peptidesHigherInCase =
                new HashSet<String>();
        Set<String> peptidesHigherInControl =
                new HashSet<String>();
        int rowsHigherInControl = 0;
        int rowsHigherInCase = 0;


        double[] logIntensitiesCase = null;
        double[] logIntensitiesControl = null;

            double[] intensitiesCasearray = null;
            double[] intensitiesControlarray = null;

        try
        {
            List<Double> intensitiesCase = new ArrayList<Double>();
            List<Double> intensitiesControl = new ArrayList<Double>();

            List<Feature> featuresCase = new ArrayList<Feature>();
            List<Feature> featuresControl = new ArrayList<Feature>();
            int numPeptidesInAgreement = 0;
            int numPeptideConflicts=0;
            for (Object rowObj : rows)
            {
                HashMap rowMap = (HashMap) rowObj;

                int featureSupportCase = 0;
                int peptideSupportCase = 0;
                String peptide = null;
                double intensitySumCase = 0;

                for (String caseRunName : caseRunNames)
                {
                    Object runIntensity = rowMap.get("intensity_" + caseRunName);
                    if (runIntensity == null && !add1)
                        continue;
                    try
                    {
                        intensitySumCase += Double.parseDouble(runIntensity.toString());
                    }
                    catch (Exception e){}
                    featureSupportCase++;
                    String thisPeptide = (String) rowMap.get("peptide_" + caseRunName);
                    if (thisPeptide == null && !add1)
                        continue;


                    if (add1)
                    {
                        if (peptide == null)
                        {
                            peptide = thisPeptide;
                        }                        
                        if (peptide != null && thisPeptide != null &&
                            !peptide.equals(thisPeptide))
                        {
                                numPeptideConflicts++;
                                peptideSupportCase = 0;
                                break;                            
                        }
                    }
                    else
                    {
                        if (peptide == null)
                        {
                            peptide = thisPeptide;
                            peptideSupportCase++;
                        }
                        else
                        {
                            try
                            {
                                if (peptide.equals(thisPeptide))
                                    peptideSupportCase++;
                                else
                                {
                                    numPeptideConflicts++;
                                    peptideSupportCase =0;
                                    break;
                                }
                            }
                            catch (Exception e) {}
                        }
                    }
                }

                double intensityMeanCase = 0;
                if (featureSupportCase >= minFeatureSupport)
                {
                        intensityMeanCase = intensitySumCase / featureSupportCase;
                }

                double intensitySumControl = 0;
                int featureSupportControl = 0;
                int peptideSupportControl = 0;
                boolean peptideConflict = false;
                for (String controlRunName : controlRunNames)
                {
                    Object runIntensity = rowMap.get("intensity_" + controlRunName);
                    if (runIntensity == null && !add1)
                        continue;
                    try
                    {
                        intensitySumControl += Double.parseDouble(runIntensity.toString());
                    }
                    catch (Exception e){}

                    featureSupportControl++;
                    String thisPeptide = (String) rowMap.get("peptide_" + controlRunName);
                    if (thisPeptide == null && !add1)
                        continue;

                    if (add1)
                    {
                        if (peptide == null)
                        {
                            peptide = thisPeptide;
                        }
                        if (peptide != null && thisPeptide != null &&
                            !peptide.equals(thisPeptide))
                        {
                                numPeptideConflicts++;
                                peptideSupportControl = 0;
                                peptideConflict = true;
                                break;
                        }
                    }
                    else
                    {
                        if (peptide == null)
                        {
                            peptide = thisPeptide;
                            peptideSupportControl++;
                        }
                        else
                        {
                            if (peptide.equals(thisPeptide))
                                peptideSupportControl++;
                            else
                            {
                                numPeptideConflicts++;
                                peptideSupportControl = 0;
                                peptideConflict = true;
                                break;
                            }
                        }
                    }


                }

                double intensityMeanControl = 0;
                if (featureSupportControl >= minFeatureSupport)
                {
                    intensityMeanControl = intensitySumControl / featureSupportControl;
                }

//if (peptide != null) System.err.println(peptide);

                boolean peptideAgreement = false;
                if (peptideSupportControl + peptideSupportCase >= minPeptideSupport &&
                        featureSupportCase >= minFeatureSupport &&
                        featureSupportControl >= minFeatureSupport)
                {
                    numPeptidesInAgreement++;
                    peptideAgreement=true;
                }
                if (add1)
                {
                    intensityMeanControl++;
                    intensityMeanCase++;                     
                }

                if (peptide != null && (peptideAgreement || add1))
                {
//if (!peptideAgreement) System.err.println("no-agree peptide, peptide = " + peptide + ", add1= " + add1);
                    double caseControlRatio = intensityMeanCase / intensityMeanControl;
                    if (caseControlRatio > minSignificantRatio)
                    {
                        rowsHigherInCase++;
                        if (peptideAgreement)
                            peptidesHigherInCase.add(peptide);
                    }
                    else if (1 / caseControlRatio > minSignificantRatio)
                    {
                        rowsHigherInControl++;
                        peptidesHigherInControl.add(peptide);
                    }

                    intensitiesCase.add(intensityMeanCase);
                    intensitiesControl.add(intensityMeanControl);
                }

                if (!peptideConflict && (peptide != null))
                {
                    if (add1 || peptideAgreement)
                    {
//if (!peptideAgreement) System.err.println("Adding one we wouldn't otherwise, add1 is " + add1 + ", mode is " + mode);
                        Feature controlFeature = new Feature(1,1000,(float) intensityMeanControl);
                        MS2ExtraInfoDef.addPeptide(controlFeature, peptide);
                        featuresControl.add(controlFeature);

                        Feature caseFeature = new Feature(1,1000,(float) intensityMeanCase);
                        MS2ExtraInfoDef.addPeptide(caseFeature, peptide);
                        featuresCase.add(caseFeature);
                    }
                }


            }


            ApplicationContext.infoMessage("# Peptides in agreement: " + numPeptidesInAgreement);
            ApplicationContext.infoMessage("# Peptide conflicts: " + numPeptideConflicts);

            ApplicationContext.infoMessage("# Peptides higher in case: " + peptidesHigherInCase.size() + " peptides in " + rowsHigherInCase + " array rows");
            ApplicationContext.infoMessage("# Peptides higher in control: " + peptidesHigherInControl.size() + " peptides in " + rowsHigherInControl + " array rows");
            ApplicationContext.infoMessage("# Peptides higher in one or the other: " + (peptidesHigherInCase.size() + peptidesHigherInControl.size() +
                               " peptides in " + (rowsHigherInCase + rowsHigherInControl) + " array rows"));


            intensitiesCasearray = new double[featuresCase.size()];
            intensitiesControlarray = new double[featuresCase.size()];

            logIntensitiesCase = new double[featuresCase.size()];
            logIntensitiesControl = new double[featuresCase.size()];

            int numInsideTwofold=0;
            for (int i=0; i<intensitiesCasearray.length; i++)
            {
                intensitiesCasearray[i] = featuresCase.get(i).getIntensity();
                intensitiesControlarray[i] = featuresControl.get(i).getIntensity();

                logIntensitiesCase[i] = Math.log(intensitiesCasearray[i]);
                logIntensitiesControl[i] = Math.log(intensitiesControlarray[i]);                

                double intensitiesRatio = intensitiesCasearray[i] / intensitiesControlarray[i];

//intensitiesRatio = intensitiesRatio * 5.0/4.0;

                if (intensitiesRatio > 0.5 && intensitiesRatio < 2.0)
                    numInsideTwofold++;
            }

            if (showCharts)
            {
                System.err.println("dots on plot: " + intensitiesControlarray.length);
                ScatterPlotDialog spd = new ScatterPlotDialog();
                spd.addData(intensitiesControlarray, intensitiesCasearray, "intensities: x is control, y is case");
                spd.setVisible(true);
                ScatterPlotDialog spd2 = new ScatterPlotDialog();
                spd2.addData(logIntensitiesControl, logIntensitiesCase, "intensities: x is log control, y is log case");

////this is special-purpose
//double[] lineX = new double[1300];
//double[] lineY = new double[1300];
//for (int c=1; c<lineX.length; c++)
//{
//    lineX[c] = Math.log(c);
//    lineY[c] = Math.log(4.0 * c / 5.0);
//}
//spd.addData(lineX, lineY,"4:5 line");


                spd2.setVisible(true);
            }
            System.err.println("Same peptide intensity summary:");
System.err.println("Within twofold: " + numInsideTwofold + " out of " + intensitiesCasearray.length);

            if (outFile != null)
            {
                ApplicationContext.infoMessage("Writing ratios to file " + outFile.getAbsolutePath());
                PrintWriter outPW = null;
                try
                {

                    outPW = new PrintWriter(outFile);
                    outPW.println("peptide\tratio");
                    for (int i=0; i<featuresCase.size(); i++)
                    {
                        String peptide = MS2ExtraInfoDef.getFirstPeptide(featuresCase.get(i));
                        outPW.println(peptide + "\t" + (intensitiesCasearray[i] / intensitiesControlarray[i]));
                    }
                }
                catch (Exception e)
                {
                    throw new CommandLineModuleExecutionException(e);
                }
                finally
                {
                    if (outPW != null)
                        outPW.close();
                }
                ApplicationContext.infoMessage("Done writing ratios");
            }

//            if (outDir != null)
//            {
//                FeatureSet caseFeatureSet =
//                        new FeatureSet(featuresCase.toArray(new Feature[featuresCase.size()]));
//                FeatureSet controlFeatureSet =
//                        new FeatureSet(featuresControl.toArray(new Feature[featuresControl.size()]));
//
//                File caseFile = new File(outDir, "case.peptides.tsv");
//                caseFeatureSet.save(caseFile);
//                ApplicationContext.setMessage("Wrote case features to " + caseFile.getAbsolutePath());
//
//                File controlFile = new File(outDir, "control.peptides.tsv");
//                controlFeatureSet.save(controlFile);
//                ApplicationContext.setMessage("Wrote control features to " + controlFile.getAbsolutePath());
//            }

        }
        catch (Exception e)
        {
            throw new CommandLineModuleExecutionException(e);
        }

        return new Pair<double[], double[]>(intensitiesCasearray, intensitiesControlarray);
    }


    public void compareNonPeptideIntensities()
    {
        Map<String,Object>[] rowMaps = peptideArrayAnalyzer.getRowMaps();
        double ratioSum = 0;

        List<Pair<Double,Double>> caseControlIntensityPairs =
                new ArrayList<Pair<Double,Double>>();

        for (Map<String,Object> rowMap : rowMaps)
        {
            Object peptideObject = null;
            double intensitySumCase = 0;
            for (String caseRunName : caseRunNames)
            {
                Object runIntensity = rowMap.get("intensity_" + caseRunName);
                if (runIntensity == null)
                    continue;
                intensitySumCase += Double.parseDouble(runIntensity.toString());
                if (peptideObject == null)
                {
                    peptideObject = rowMap.get("peptide_" + caseRunName);
                }
            }

            if (peptideObject != null)
            {
System.err.println("Tossing peptide " + peptideObject.toString());
                continue;
            }

            if (intensitySumCase == 0)
                continue;

            double intensitySumControl = 0;
            for (String controlRunName : controlRunNames)
            {
                Object runIntensity = rowMap.get("intensity_" + controlRunName);
                if (runIntensity == null)
                    continue;
                intensitySumControl += Double.parseDouble(runIntensity.toString());
                if (peptideObject == null)
                {
                    peptideObject = rowMap.get("peptide_" + controlRunName);
                }
            }

            if (intensitySumControl == 0)
                continue;
            if (peptideObject != null)
            {
System.err.println("  Tossing peptide " + peptideObject.toString());                
                continue;
            }

            caseControlIntensityPairs.add(new Pair<Double,Double>(intensitySumCase, intensitySumControl));
            ratioSum += intensitySumCase / intensitySumControl;
        }

        double[] caseIntensities = new double[caseControlIntensityPairs.size()];
        double[] controlIntensities = new double[caseControlIntensityPairs.size()];

        for (int i=0; i<caseControlIntensityPairs.size(); i++)
        {
            caseIntensities[i] = caseControlIntensityPairs.get(i).first;
            controlIntensities[i] = caseControlIntensityPairs.get(i).second;
        }

        double[] logCaseIntensities = new double[caseControlIntensityPairs.size()];
        double[] logControlIntensities = new double[caseControlIntensityPairs.size()];

        for (int i=0; i<caseControlIntensityPairs.size(); i++)
        {
            logCaseIntensities[i] = Math.log(caseControlIntensityPairs.get(i).first);
            logControlIntensities[i] = Math.log(caseControlIntensityPairs.get(i).second);
        }


        double meanRatio = ratioSum / caseIntensities.length;
System.err.println("dots: " + caseIntensities.length);
        ApplicationContext.infoMessage("Average case-control intensity ratio: " + meanRatio);
        ScatterPlotDialog spd = new ScatterPlotDialog(controlIntensities, caseIntensities, "X is control, y is case");
        spd.setVisible(true);

        ScatterPlotDialog spd2 = new ScatterPlotDialog(logControlIntensities, logCaseIntensities, "X is log control, y is log case");
        spd2.setVisible(true);
    }



}
