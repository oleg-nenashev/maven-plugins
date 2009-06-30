package org.apache.maven.plugin.antrun;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.antrun.components.AntTargetConverter;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.types.Path;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.util.StringUtils;

/**
 * Abstract class for the Antrun plugin
 *
 * @author <a href="mailto:kenney@apache.org">Kenney Westerhof</a>
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
public abstract class AbstractAntMojo
    extends AbstractMojo
{
    /**
     * @deprecated use {@link AbstractAntMojo#executeTasks(Target,MavenProject,List)}.
     */
    protected void executeTasks( Target antTasks, MavenProject mavenProject )
        throws MojoExecutionException
    {
        executeTasks( antTasks, mavenProject, null );
    }

    /**
     * @param antTasks
     * @param mavenProject
     * @throws MojoExecutionException
     */
    protected void executeTasks( Target antTasks, MavenProject mavenProject, List pluginArtifacts )
        throws MojoExecutionException
    {
        if ( antTasks == null )
        {
            getLog().info( "No ant tasks defined - SKIPPED" );
            return;
        }

        try
        {
            //TODO refactor - place the manipulation of the expressionEvaluator into a separated class.
            ExpressionEvaluator exprEvaluator = (ExpressionEvaluator) antTasks.getProject()
                .getReference( AntTargetConverter.MAVEN_EXPRESSION_EVALUATOR_ID );

            Project antProject = antTasks.getProject();

            PropertyHelper propertyHelper = PropertyHelper.getPropertyHelper( antProject );
            propertyHelper.setNext( new AntPropertyHelper( exprEvaluator, mavenProject.getArtifacts(), getLog() ) );

            DefaultLogger antLogger = new DefaultLogger();
            antLogger.setOutputPrintStream( System.out );
            antLogger.setErrorPrintStream( System.err );
            antLogger.setMessageOutputLevel( getLog().isDebugEnabled() ? Project.MSG_DEBUG : Project.MSG_INFO );

            antProject.addBuildListener( antLogger );
            antProject.setBaseDir( mavenProject.getBasedir() );

            Path p = new Path( antProject );
            p.setPath( StringUtils.join( mavenProject.getCompileClasspathElements().iterator(), File.pathSeparator ) );

            /* maven.dependency.classpath it's deprecated as it's equal to maven.compile.classpath */
            antProject.addReference( "maven.dependency.classpath", p );
            antProject.addReference( "maven.compile.classpath", p );

            p = new Path( antProject );
            p.setPath( StringUtils.join( mavenProject.getRuntimeClasspathElements().iterator(), File.pathSeparator ) );
            antProject.addReference( "maven.runtime.classpath", p );

            p = new Path( antProject );
            p.setPath( StringUtils.join( mavenProject.getTestClasspathElements().iterator(), File.pathSeparator ) );
            antProject.addReference( "maven.test.classpath", p );

            /* set maven.plugin.classpath with plugin dependencies */
            antProject.addReference( "maven.plugin.classpath", getPathFromArtifacts( pluginArtifacts, antProject ) );
            
            // The ant project needs actual properties vs. using expression evaluator when calling an external build file.
            copyProperties( mavenProject, antProject );

            if ( getLog().isInfoEnabled() )
            {
                getLog().info( "Executing tasks" );
            }

            antTasks.execute();

            if ( getLog().isInfoEnabled() )
            {
                getLog().info( "Executed tasks" );
            }
        }
        catch ( DependencyResolutionRequiredException e )
        {
            throw new MojoExecutionException( "DependencyResolutionRequiredException: " + e.getMessage(), e );
        }
        catch ( BuildException e )
        {
            throw new MojoExecutionException( "An Ant BuildException has occured: " + e.getMessage(), e );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Error executing ant tasks: " + e.getMessage(), e );
        }
    }

    /**
     * @param artifacts
     * @param antProject
     * @return a path
     * @throws DependencyResolutionRequiredException
     */
    public Path getPathFromArtifacts( Collection artifacts, Project antProject )
        throws DependencyResolutionRequiredException
    {
        if ( artifacts == null )
        {
            return new Path( antProject );
        }

        List list = new ArrayList( artifacts.size() );
        for ( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();
            File file = a.getFile();
            if ( file == null )
            {
                throw new DependencyResolutionRequiredException( a );
            }
            list.add( file.getPath() );
        }

        Path p = new Path( antProject );
        p.setPath( StringUtils.join( list.iterator(), File.pathSeparator ) );

        return p;
    }

    /**
     * Copy properties from the maven project to the ant project.
     * @param mavenProject
     * @param antProject
     */
    public void copyProperties( MavenProject mavenProject, Project antProject )
    {
        Properties mavenProps = mavenProject.getProperties();
        Iterator iter = mavenProps.keySet().iterator();
        while ( iter.hasNext() )
        {
            String key = (String)iter.next();
            antProject.setProperty( key, mavenProps.getProperty( key ) );
        }
        
        // Add some of the common maven properties
        antProject.setProperty( "maven.project.groupId", mavenProject.getGroupId() );
        antProject.setProperty( "maven.project.artifactId", mavenProject.getArtifactId() );
        antProject.setProperty( "maven.project.name", mavenProject.getName() );
        antProject.setProperty( "maven.project.description", mavenProject.getDescription() );
        antProject.setProperty( "maven.project.version", mavenProject.getVersion() );
        antProject.setProperty( "maven.project.packaging", mavenProject.getPackaging() );
        antProject.setProperty( "maven.project.build.directory", mavenProject.getBuild().getDirectory() );
        antProject.setProperty( "maven.project.build.outputDirectory", mavenProject.getBuild().getOutputDirectory() );
        antProject.setProperty( "maven.project.build.outputDirectory", mavenProject.getBuild().getTestOutputDirectory() );
        antProject.setProperty( "maven.project.build.sourceDirectory", mavenProject.getBuild().getSourceDirectory() );
        antProject.setProperty( "maven.project.build.testSourceDirectory", mavenProject.getBuild().getTestSourceDirectory() );
        
        // Add paths to depenedency artifacts
        Set artifacts = mavenProject.getDependencyArtifacts();
        for ( Iterator it = artifacts.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            String key = AntPropertyHelper.getDependencyArtifactPropertyName( artifact );

            antProject.setProperty( key, artifact.getFile().getPath() );
        }
    }
}
