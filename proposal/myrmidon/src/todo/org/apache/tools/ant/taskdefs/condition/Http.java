/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.apache.tools.ant.taskdefs.condition;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.apache.myrmidon.api.TaskException;
import org.apache.tools.ant.ProjectComponent;

/**
 * Condition to wait for a HTTP request to succeed. Its attribute(s) are: url -
 * the URL of the request.
 *
 * @author <a href="mailto:denis@network365.com">Denis Hennessy</a>
 */
public class Http
    extends ProjectComponent
    implements Condition
{
    String spec = null;

    public void setUrl( String url )
    {
        spec = url;
    }

    public boolean eval()
        throws TaskException
    {
        if( spec == null )
        {
            throw new TaskException( "No url specified in HTTP task" );
        }
        getLogger().debug( "Checking for " + spec );
        try
        {
            URL url = new URL( spec );
            try
            {
                URLConnection conn = url.openConnection();
                if( conn instanceof HttpURLConnection )
                {
                    HttpURLConnection http = (HttpURLConnection)conn;
                    int code = http.getResponseCode();
                    getLogger().debug( "Result code for " + spec + " was " + code );
                    if( code > 0 && code < 500 )
                    {
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
            }
            catch( java.io.IOException e )
            {
                return false;
            }
        }
        catch( MalformedURLException e )
        {
            throw new TaskException( "Badly formed URL: " + spec, e );
        }
        return true;
    }
}
