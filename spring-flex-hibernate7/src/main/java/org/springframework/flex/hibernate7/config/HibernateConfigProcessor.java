/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.flex.hibernate7.config;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.ComponentType;
import org.hibernate.type.Type;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.flex.config.MessageBrokerConfigProcessor;
import org.springframework.flex.core.io.AbstractAmfConversionServiceConfigProcessor;
import org.springframework.flex.hibernate7.HibernateProxyConverter;
import org.springframework.flex.hibernate7.PersistentCollectionConverterFactory;
import org.springframework.util.Assert;

import flex.messaging.io.PropertyProxyRegistry;

/**
 * {@link MessageBrokerConfigProcessor} implementation that uses the Hibernate Metadata API to determine all classes that potentially need
 * special AMF conversion rules applied to them to prevent lazy initialization errors.  Each type found will have a properly configured
 * instance of {@link org.springframework.flex.core.io.SpringPropertyProxy} registered for it with the BlazeDS {@link PropertyProxyRegistry}.
 *
 * <p>Hibernate 6 dropped the {@code org.hibernate.metadata.ClassMetadata} / {@code CollectionMetadata} interfaces. This implementation
 * works directly against {@link EntityPersister} and {@link CollectionPersister} obtained from the {@link MappingMetamodel}.
 *
 * @author Jeremy Grelle
 */
public class HibernateConfigProcessor extends AbstractAmfConversionServiceConfigProcessor implements BeanFactoryAware, InitializingBean {

    private final Set<EntityPersister> entityPersisters = new HashSet<EntityPersister>();

    private final Set<CollectionPersister> collectionPersisters = new HashSet<CollectionPersister>();

    private ListableBeanFactory beanFactory;

    protected boolean hibernateConfigured = false;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        if (!this.hibernateConfigured) {
            if (BeanFactoryUtils.beanNamesForTypeIncludingAncestors(getBeanFactory(), SessionFactory.class).length > 0) {
                for (SessionFactory sessionFactory : BeanFactoryUtils.beansOfTypeIncludingAncestors(beanFactory, SessionFactory.class).values()) {
                    extractHibernateMetadata(sessionFactory);
                }
            }
        }
        super.afterPropertiesSet();
    }

    /**
     *
     * {@inheritDoc}
     */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ListableBeanFactory.class, beanFactory, "HibernateConfigProcessor must be used in a ListableBeanFactory in order to auto-detect the necessary Hibernate configuration.");
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    /**
     * Sets the Hibernate {@link SessionFactory} to be used for reading type metadata.  If this property is not
     * explicitly set, all available {@code SessionFactory} instances will be retrieved from the containing
     * {@link BeanFactory} and have their type metadata extracted for use in {@link #findTypesToRegister()}
     * @param sessionFactory the session factory from which to read metadata
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
        extractHibernateMetadata(sessionFactory);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected Set<Class<?>> findTypesToRegister() {
        Set<Class<?>> typesToRegister = new HashSet<Class<?>>();
        if (hibernateConfigured) {
            for (EntityPersister persister : this.entityPersisters) {
                Class<?> mappedClass = persister.getMappedClass();
                if (mappedClass != null && !mappedClass.isInterface()) {
                    typesToRegister.add(mappedClass);
                    findComponentProperties(persister.getPropertyTypes(), typesToRegister);
                }
            }
            for (CollectionPersister persister : this.collectionPersisters) {
                Type elementType = persister.getElementType();
                if (elementType instanceof ComponentType) {
                    Class<?> returnedClass = elementType.getReturnedClass();
                    if (returnedClass != null && !returnedClass.isInterface()) {
                        typesToRegister.add(returnedClass);
                        findComponentProperties(((ComponentType) elementType).getSubtypes(), typesToRegister);
                    }
                }
            }
        }
        return typesToRegister;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void configureConverters(ConverterRegistry registry) {
        registry.addConverter(new HibernateProxyConverter());
        registry.addConverterFactory(new PersistentCollectionConverterFactory());
    }

    /**
     * Provides access to the {@link BeanFactory} by subclasses.
     * @return the containing bean factory
     */
    protected ListableBeanFactory getBeanFactory() {
        return beanFactory;
    }

    /**
     * Extracts all entity and collection persisters from a given {@link SessionFactory} to determine the types that
     * need a {@link org.springframework.flex.core.io.SpringPropertyProxy} registered in {@link #findTypesToRegister()}.
     *
     * @param sessionFactory the session factory from which to read metadata
     */
    protected void extractHibernateMetadata(SessionFactory sessionFactory) {
        SessionFactoryImplementor sfi = sessionFactory.unwrap(SessionFactoryImplementor.class);
        MappingMetamodel mappingMetamodel = sfi.getMappingMetamodel();

        mappingMetamodel.forEachEntityDescriptor(this.entityPersisters::add);
        mappingMetamodel.forEachCollectionDescriptor(this.collectionPersisters::add);

        this.hibernateConfigured = true;
    }

    private void findComponentProperties(Type[] propertyTypes, Set<Class<?>> typesToRegister) {
        if (propertyTypes == null) {
            return;
        }
        for (Type propertyType : propertyTypes) {
            if (propertyType instanceof ComponentType) {
                typesToRegister.add(propertyType.getReturnedClass());
                findComponentProperties(((ComponentType) propertyType).getSubtypes(), typesToRegister);
            }
        }
    }
}
