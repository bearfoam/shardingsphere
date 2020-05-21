/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.proxy.frontend.mysql.auth;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.SneakyThrows;
import org.apache.shardingsphere.db.protocol.mysql.constant.MySQLCapabilityFlag;
import org.apache.shardingsphere.db.protocol.mysql.constant.MySQLConnectionPhase;
import org.apache.shardingsphere.db.protocol.mysql.constant.MySQLServerErrorCode;
import org.apache.shardingsphere.db.protocol.mysql.packet.generic.MySQLErrPacket;
import org.apache.shardingsphere.db.protocol.mysql.packet.generic.MySQLOKPacket;
import org.apache.shardingsphere.db.protocol.mysql.packet.handshake.MySQLHandshakePacket;
import org.apache.shardingsphere.db.protocol.mysql.payload.MySQLPacketPayload;
import org.apache.shardingsphere.kernal.context.SchemaContext;
import org.apache.shardingsphere.kernal.context.SchemaContexts;
import org.apache.shardingsphere.proxy.backend.communication.jdbc.connection.BackendConnection;
import org.apache.shardingsphere.proxy.backend.schema.ProxySchemaContexts;
import org.apache.shardingsphere.proxy.backend.schema.ShardingSphereSchema;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class MySQLAuthenticationEngineTest {

    private final MySQLAuthenticationHandler authenticationHandler = mock(MySQLAuthenticationHandler.class);

    private final MySQLAuthenticationEngine authenticationEngine = new MySQLAuthenticationEngine();

    private final byte[] authResponse = {-27, 89, -20, -27, 65, -120, -64, -101, 86, -100, -108, -100, 6, -125, -37, 117, 14, -43, 95, -113};

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        initAuthenticationHandlerForAuthenticationEngine();
    }

    private void initAuthenticationHandlerForAuthenticationEngine() throws NoSuchFieldException, IllegalAccessException {
        Field field = MySQLAuthenticationEngine.class.getDeclaredField("authenticationHandler");
        field.setAccessible(true);
        field.set(authenticationEngine, authenticationHandler);
    }

    @Test
    public void assertHandshake() {
        ChannelHandlerContext context = getContext();
        BackendConnection backendConnection = mock(BackendConnection.class);
        authenticationEngine.handshake(context, backendConnection);
        verify(context).writeAndFlush(any(MySQLHandshakePacket.class));
        verify(backendConnection).setConnectionId(anyInt());
    }
    
    @Test
    public void assertAuthenticationMethodMismatch() {
        setConnectionPhase(MySQLConnectionPhase.AUTH_PHASE_FAST_PATH);
        MySQLPacketPayload payload = mock(MySQLPacketPayload.class);
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(payload.readInt4()).thenReturn(MySQLCapabilityFlag.CLIENT_PLUGIN_AUTH.getValue());
        authenticationEngine.auth(channelHandlerContext, payload, mock(BackendConnection.class));
        assertThat(getConnectionPhase(), is(MySQLConnectionPhase.AUTHENTICATION_METHOD_MISMATCH));
    }
    
    @Test
    public void assertAuthSwitchResponse() {
        setConnectionPhase(MySQLConnectionPhase.AUTHENTICATION_METHOD_MISMATCH);
        MySQLPacketPayload payload = mock(MySQLPacketPayload.class);
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(payload.readStringEOFByBytes()).thenReturn(authResponse);
        authenticationEngine.auth(channelHandlerContext, payload, mock(BackendConnection.class));
        assertThat(getAuthResponse(), is(authResponse));
    }

    @Test
    public void assertAuthWithLoginFail() throws NoSuchFieldException, IllegalAccessException {
        setConnectionPhase(MySQLConnectionPhase.AUTH_PHASE_FAST_PATH);
        ChannelHandlerContext context = getContext();
        setSchemas(Collections.singletonMap("sharding_db", mock(ShardingSphereSchema.class)));
        when(authenticationHandler.login(anyString(), any(), anyString())).thenReturn(Optional.of(MySQLServerErrorCode.ER_ACCESS_DENIED_ERROR));
        authenticationEngine.auth(context, getPayload("root", "sharding_db", authResponse), mock(BackendConnection.class));
        verify(context).writeAndFlush(any(MySQLErrPacket.class));
    }

    @Test
    public void assertAuthWithAbsentDatabase() throws NoSuchFieldException, IllegalAccessException {
        ChannelHandlerContext context = getContext();
        setSchemas(Collections.singletonMap("sharding_db", mock(ShardingSphereSchema.class)));
        setConnectionPhase(MySQLConnectionPhase.AUTH_PHASE_FAST_PATH);
        authenticationEngine.auth(context, getPayload("root", "ABSENT DATABASE", authResponse), mock(BackendConnection.class));
        verify(context).writeAndFlush(any(MySQLErrPacket.class));
    }

    @Test
    public void assertAuth() throws NoSuchFieldException, IllegalAccessException {
        setConnectionPhase(MySQLConnectionPhase.AUTH_PHASE_FAST_PATH);
        ChannelHandlerContext context = getContext();
        when(authenticationHandler.login(anyString(), any(), anyString())).thenReturn(Optional.empty());
        setSchemas(Collections.singletonMap("sharding_db", mock(ShardingSphereSchema.class)));
        authenticationEngine.auth(context, getPayload("root", "sharding_db", authResponse), mock(BackendConnection.class));
        verify(context).writeAndFlush(any(MySQLOKPacket.class));
    }
    
    private void setSchemas(final Map<String, ShardingSphereSchema> schemas) throws NoSuchFieldException, IllegalAccessException {
        Field field = ProxySchemaContexts.getInstance().getClass().getDeclaredField("schemaContexts");
        field.setAccessible(true);
        field.set(ProxySchemaContexts.getInstance(), mockSchemaContexts());
    }
    
    private SchemaContexts mockSchemaContexts() {
        SchemaContexts result = mock(SchemaContexts.class);
        when(result.getSchemaContexts()).thenReturn(Collections.singletonMap("sharding_db", mock(SchemaContext.class)));
        return result;
    }
    
    private MySQLPacketPayload getPayload(final String userName, final String database, final byte[] authResponse) {
        MySQLPacketPayload result = mock(MySQLPacketPayload.class);
        when(result.readInt4()).thenReturn(MySQLCapabilityFlag.CLIENT_CONNECT_WITH_DB.getValue());
        when(result.readStringNul()).thenReturn(userName).thenReturn(database);
        when(result.readStringNulByBytes()).thenReturn(authResponse);
        return result;
    }

    private ChannelHandlerContext getContext() {
        ChannelHandlerContext result = mock(ChannelHandlerContext.class);
        doReturn(getChannel()).when(result).channel();
        return result;
    }

    private Channel getChannel() {
        Channel result = mock(Channel.class);
        doReturn(getRemoteAddress()).when(result).remoteAddress();
        return result;
    }

    private SocketAddress getRemoteAddress() {
        SocketAddress result = mock(SocketAddress.class);
        when(result.toString()).thenReturn("127.0.0.1");
        return result;
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void setConnectionPhase(final MySQLConnectionPhase connectionPhase) {
        Field field = MySQLAuthenticationEngine.class.getDeclaredField("connectionPhase");
        field.setAccessible(true);
        field.set(authenticationEngine, connectionPhase);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private MySQLConnectionPhase getConnectionPhase() {
        Field field = MySQLAuthenticationEngine.class.getDeclaredField("connectionPhase");
        field.setAccessible(true);
        return (MySQLConnectionPhase) field.get(authenticationEngine);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private byte[] getAuthResponse() {
        Field field = MySQLAuthenticationEngine.class.getDeclaredField("authResponse");
        field.setAccessible(true);
        return (byte[]) field.get(authenticationEngine);
    }
}
