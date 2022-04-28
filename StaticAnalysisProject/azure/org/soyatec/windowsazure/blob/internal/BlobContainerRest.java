package org.soyatec.windowsazure.blob.internal;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHeader;
import org.dom4j.Document;
import org.dom4j.Element;
import org.soyatec.windowsazure.authenticate.Base64;
import org.soyatec.windowsazure.authenticate.HttpRequestAccessor;
import org.soyatec.windowsazure.authenticate.SharedKeyCredentials;
import org.soyatec.windowsazure.authenticate.SharedKeyCredentialsWrapper;
import org.soyatec.windowsazure.blob.BlobType;
import org.soyatec.windowsazure.blob.DateTime;
import org.soyatec.windowsazure.blob.IBlob;
import org.soyatec.windowsazure.blob.IBlobConstraints;
import org.soyatec.windowsazure.blob.IBlobContents;
import org.soyatec.windowsazure.blob.IBlobProperties;
import org.soyatec.windowsazure.blob.IBlockBlob;
import org.soyatec.windowsazure.blob.IContainerAccessControl;
import org.soyatec.windowsazure.blob.IContainerProperties;
import org.soyatec.windowsazure.blob.IPageBlob;
import org.soyatec.windowsazure.blob.IRetryPolicy;
import org.soyatec.windowsazure.blob.ISharedAccessUrl;
import org.soyatec.windowsazure.blob.LeaseMode;
import org.soyatec.windowsazure.blob.LeaseStatus;
import org.soyatec.windowsazure.blob.SharedAccessPermissions;
import org.soyatec.windowsazure.blob.internal.BlobContainer;
import org.soyatec.windowsazure.blob.internal.BlobProperties;
import org.soyatec.windowsazure.blob.internal.BlockBlob;
import org.soyatec.windowsazure.blob.internal.ContainerAccessControl;
import org.soyatec.windowsazure.blob.internal.ContainerProperties;
import org.soyatec.windowsazure.blob.internal.ListBlobsResult;
import org.soyatec.windowsazure.blob.internal.PageBlob;
import org.soyatec.windowsazure.blob.internal.SSLProperties;
import org.soyatec.windowsazure.error.StorageErrorCode;
import org.soyatec.windowsazure.error.StorageException;
import org.soyatec.windowsazure.error.StorageServerException;
import org.soyatec.windowsazure.internal.AccessPolicy;
import org.soyatec.windowsazure.internal.OutParameter;
import org.soyatec.windowsazure.internal.ResourceUriComponents;
import org.soyatec.windowsazure.internal.SignedIdentifier;
import org.soyatec.windowsazure.internal.constants.HttpWebResponse;
import org.soyatec.windowsazure.internal.util.HttpUtilities;
import org.soyatec.windowsazure.internal.util.Logger;
import org.soyatec.windowsazure.internal.util.NameValueCollection;
import org.soyatec.windowsazure.internal.util.TimeSpan;
import org.soyatec.windowsazure.internal.util.Utilities;
import org.soyatec.windowsazure.internal.util.xml.AtomUtil;
import org.soyatec.windowsazure.internal.util.xml.XPathQueryHelper;
import org.soyatec.windowsazure.internal.util.xml.XmlUtil;

public class BlobContainerRest extends BlobContainer {

   private byte[] key;
   SharedKeyCredentials credentials;
   private ISharedAccessUrl shareAccessUrl;


   public BlobContainerRest(URI baseUri, boolean usePathStyleUris, String accountName, String containerName, String base64Key, Timestamp lastModified, TimeSpan timeOut, IRetryPolicy retryPolicy) {
      super(baseUri, usePathStyleUris, accountName, containerName, lastModified);
      ResourceUriComponents uriComponents = new ResourceUriComponents(accountName, containerName, (String)null);
      URI containerUri = HttpRequestAccessor.constructResourceUri(baseUri, uriComponents, usePathStyleUris);
      this.setUri(containerUri);
      if(base64Key != null) {
         this.key = Base64.decode(base64Key);
      }

      this.credentials = new SharedKeyCredentials(accountName, this.key);
      this.setTimeout(timeOut);
      this.setRetryPolicy(retryPolicy);
   }

   public void setSSLProperty(String keystore, String keystorePasswd, String truststore, String truststorepasswd, String keyalias) {
      SSLProperties.setSSLSettings(keystore, keystorePasswd, truststore, truststorepasswd, keyalias);
   }

   public void clearSSLProperty() {
      SSLProperties.clearSSLSettings();
   }

   private boolean createOrUpdateBlockBlob(IBlobProperties blobProperties, IBlobContents blobContents, boolean overwrite) throws StorageException {
      String blobName = blobProperties.getName();
      if(blobName != null && !blobName.equals("")) {
         if(blobName.lastIndexOf(46) == blobName.length() - 1) {
            throw new IllegalArgumentException(MessageFormat.format("The specified blob name \"{0}\" is not valid!\nPlease choose a name that conforms to the naming conventions for blob!\nSee <a>http://msdn.microsoft.com/en-us/library/dd135715.aspx</a> for more information.", new Object[]{blobName}));
         } else {
            try {
               BlockBlob e = (BlockBlob)this.getBlockBlobReference(blobName);
               boolean putBlobImpl = e.putBlobImpl(blobProperties, blobContents.getStream(), overwrite, (String)null);
               return putBlobImpl;
            } catch (Exception var7) {
               throw HttpUtilities.translateWebException(var7);
            }
         }
      } else {
         throw new IllegalArgumentException("Blob name is empty.");
      }
   }

   public boolean isContainerExist() throws StorageException {
      boolean result = false;
      result = ((Boolean)this.getRetryPolicy().execute(new Callable() {
         public Boolean call() throws Exception {
            ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null);
            NameValueCollection queryParams = new NameValueCollection();
            queryParams.put("restype", "container");
            URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null, BlobContainerRest.this.getTimeout(), queryParams, uriComponents);
            HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "GET", BlobContainerRest.this.getTimeout());
            request.addHeader("x-ms-version", "2009-07-17");
            BlobContainerRest.this.credentials.signRequest(request, uriComponents);

            try {
               HttpWebResponse we = HttpUtilities.getResponse(request);
               if(we.getStatusCode() == 200) {
                  we.close();
                  return Boolean.valueOf(true);
               } else if(we.getStatusCode() != 410 && we.getStatusCode() != 404) {
                  HttpUtilities.processUnexpectedStatusCode(we);
                  return Boolean.valueOf(false);
               } else {
                  we.close();
                  return Boolean.valueOf(false);
               }
            } catch (StorageException var6) {
               throw HttpUtilities.translateWebException(var6);
            }
         }
      })).booleanValue();
      return result;
   }

   public boolean isBlobExist(String blobName) throws StorageException {
      try {
         return this.getBlockBlobReference(blobName).getProperties() != null;
      } catch (Exception var4) {
         if(var4 instanceof StorageException) {
            StorageException se = (StorageException)var4;
            if(se.getStatusCode() == 404) {
               return false;
            } else {
               throw se;
            }
         } else {
            return false;
         }
      }
   }

   public IContainerProperties getProperties() throws StorageException {
      IContainerProperties result = null;

      try {
         result = (IContainerProperties)this.getRetryPolicy().execute(new Callable() {
            public ContainerProperties call() throws Exception {
               ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null);
               NameValueCollection queryParams = new NameValueCollection();
               queryParams.put("restype", "container");
               URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null, BlobContainerRest.this.getTimeout(), queryParams, uriComponents);
               HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "GET", BlobContainerRest.this.getTimeout());
               request.addHeader("x-ms-version", "2009-07-17");
               BlobContainerRest.this.credentials.signRequest(request, uriComponents);
               HttpWebResponse response = HttpUtilities.getResponse(request);
               if(response.getStatusCode() == 200) {
                  return BlobContainerRest.this.containerPropertiesFromResponse(response);
               } else if(response.getStatusCode() != 410 && response.getStatusCode() != 404) {
                  HttpUtilities.processUnexpectedStatusCode(response);
                  return null;
               } else {
                  response.close();
                  return null;
               }
            }
         });
         return result;
      } catch (StorageException var3) {
         throw HttpUtilities.translateWebException(var3);
      }
   }

   private ContainerProperties containerPropertiesFromResponse(HttpWebResponse response) {
      ContainerProperties prop = new ContainerProperties(this.getName());
      prop.setLastModifiedTime(response.getLastModified());
      prop.setETag(response.getHeader("ETag"));
      prop.setUri(this.getUri());
      prop.setMetadata(this.metadataFromHeaders(response.getHeaders()));
      return prop;
   }

   NameValueCollection metadataFromHeaders(NameValueCollection headers) {
      int prefixLength = "x-ms-meta-".length();
      NameValueCollection metadataEntries = new NameValueCollection();
      Iterator var5 = headers.keySet().iterator();

      while(var5.hasNext()) {
         Object key = var5.next();
         String headerName = (String)key;
         if(headerName.toLowerCase().startsWith("x-ms-meta-")) {
            metadataEntries.putAll(headerName.substring(prefixLength), headers.getCollection(headerName));
         }
      }

      return metadataEntries;
   }

   public void setAccessControl(final IContainerAccessControl acl) throws StorageException {
      try {
         this.getRetryPolicy().execute(new Callable() {
            public Boolean call() throws Exception {
               NameValueCollection queryParams = new NameValueCollection();
               queryParams.put("restype", "container");
               queryParams.put("comp", "acl");
               ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null);
               URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null, BlobContainerRest.this.getTimeout(), queryParams, uriComponents);
               HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "PUT", BlobContainerRest.this.getTimeout());
               request.addHeader("x-ms-prop-publicaccess", String.valueOf(acl.isPublic()));
               BlobContainerRest.this.addVerisonHeader(request);
               this.attachBody(acl, request);
               BlobContainerRest.this.credentials.signRequest(request, uriComponents);
               HttpWebResponse response = HttpUtilities.getResponse(request);
               if(response.getStatusCode() != 200) {
                  HttpUtilities.processUnexpectedStatusCode(response);
               } else {
                  response.close();
               }

               return Boolean.valueOf(true);
            }
            private void attachBody(IContainerAccessControl aclx, HttpRequest request) {
               String atom = AtomUtil.convertACLToXml(aclx);
               ((HttpEntityEnclosingRequest)request).setEntity(new ByteArrayEntity(atom.getBytes()));
            }
         });
      } catch (StorageException var3) {
         throw HttpUtilities.translateWebException(var3);
      }
   }

   private void addVerisonHeader(HttpRequest request) {
      request.addHeader("x-ms-version", "2009-07-17");
   }

   public ContainerAccessControl getAccessControl() throws StorageException {
      ContainerAccessControl accessControl = IContainerAccessControl.Private;

      try {
         accessControl = (ContainerAccessControl)this.getRetryPolicy().execute(new Callable() {
            public ContainerAccessControl call() throws Exception {
               NameValueCollection queryParams = new NameValueCollection();
               queryParams.put("comp", "acl");
               queryParams.put("restype", "container");
               ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null);
               URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null, BlobContainerRest.this.getTimeout(), queryParams, uriComponents);
               HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "GET", BlobContainerRest.this.getTimeout());
               request.addHeader("x-ms-version", "2009-07-17");
               BlobContainerRest.this.credentials.signRequest(request, uriComponents);
               HttpWebResponse response = HttpUtilities.getResponse(request);
               if(response.getStatusCode() == 200) {
                  String acl = response.getHeader("x-ms-prop-publicaccess");
                  boolean publicAcl = false;
                  if(acl == null) {
                     response.close();
                     throw new StorageServerException(StorageErrorCode.ServiceBadResponse, "The server did not respond with expected container access control header", response.getStatusCode(), (Exception)null);
                  } else {
                     publicAcl = Boolean.parseBoolean(acl);
                     List identifiers = BlobContainerRest.this.getSignedIdentifiersFromResponse(response);
                     ContainerAccessControl aclEntity = null;
                     if(identifiers != null && identifiers.size() > 0) {
                        aclEntity = new ContainerAccessControl(publicAcl);
                        aclEntity.setSigendIdentifiers(identifiers);
                     } else {
                        aclEntity = publicAcl?IContainerAccessControl.Public:IContainerAccessControl.Private;
                     }

                     response.close();
                     return aclEntity;
                  }
               } else {
                  HttpUtilities.processUnexpectedStatusCode(response);
                  return null;
               }
            }
         });
         return accessControl;
      } catch (Exception var3) {
         throw HttpUtilities.translateWebException(var3);
      }
   }

   private List getSignedIdentifiersFromResponse(HttpWebResponse response) {
      InputStream stream = response.getStream();
      if(stream == null) {
         return Collections.EMPTY_LIST;
      } else {
         try {
            Document e = XmlUtil.load(stream, "Container access control parsed error.");
            List selectNodes = e.selectNodes(XPathQueryHelper.SignedIdentifierListQuery);
            ArrayList result = new ArrayList();
            SignedIdentifier identifier;
            if(selectNodes.size() > 0) {
               for(Iterator iter = selectNodes.iterator(); iter.hasNext(); result.add(identifier)) {
                  Element element = (Element)iter.next();
                  identifier = new SignedIdentifier();
                  identifier.setId(XPathQueryHelper.loadSingleChildStringValue(element, "Id", true));
                  AccessPolicy policy = new AccessPolicy();
                  Element accesPlocy = (Element)element.selectSingleNode("AccessPolicy");
                  if(accesPlocy != null && accesPlocy.hasContent()) {
                     policy.setStart(new DateTime(XPathQueryHelper.loadSingleChildStringValue(accesPlocy, "Start", true)));
                     policy.setExpiry(new DateTime(XPathQueryHelper.loadSingleChildStringValue(accesPlocy, "Expiry", true)));
                     policy.setPermission(SharedAccessPermissions.valueOf(XPathQueryHelper.loadSingleChildStringValue(accesPlocy, "Permission", true)));
                     identifier.setPolicy(policy);
                  }
               }
            }

            return result;
         } catch (Exception var11) {
            Logger.error("Parse container accesss control error", var11);
            return Collections.EMPTY_LIST;
         }
      }
   }

   public boolean deleteBlob(String name) throws StorageException {
      return this.deleteBlobImpl(name, (String)null, new OutParameter(Boolean.valueOf(false)));
   }

   public boolean deleteBlobIfNotModified(IBlobProperties blob) throws StorageException {
      OutParameter modified = new OutParameter(Boolean.valueOf(false));
      boolean result = this.deleteBlobImpl(blob.getName(), blob.getETag(), modified);
      if(((Boolean)modified.getValue()).booleanValue()) {
         throw new StorageException("The blob was not deleted because it was modified.");
      } else {
         return result;
      }
   }

   private boolean deleteBlobImpl(final String name, final String eTag, OutParameter unused) throws StorageException {
      if(Utilities.isNullOrEmpty(name)) {
         throw new IllegalArgumentException("Blob name cannot be null or empty!");
      } else {
         final OutParameter retval = new OutParameter(Boolean.valueOf(false));
         final OutParameter localModified = new OutParameter(Boolean.valueOf(false));
         this.getRetryPolicy().execute(new Callable() {
            public Boolean call() throws Exception {
               String container = BlobContainerRest.this.getName();
               if(container.equals("$root")) {
                  container = "";
               }

               ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), container, name);
               URI blobUri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), container, name, BlobContainerRest.this.getTimeout(), new NameValueCollection(), uriComponents, BlobContainerRest.this.credentials);
               HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(blobUri, "DELETE", BlobContainerRest.this.getTimeout());
               //request.addHeader("x-ms-version", "2009-07-17");
               request.addHeader("x-ms-version", "2009-09-19");
               if(!Utilities.isNullOrEmpty(eTag)) {
                  request.addHeader("If-Match", eTag);
               }

               BlobContainerRest.this.credentials.signRequest(request, uriComponents);

               try {
                  HttpWebResponse ioe = HttpUtilities.getResponse(request);
                  int status = ioe.getStatusCode();
                  if(status != 200 && status != 202) {
                     if(status != 404 && status != 410) {
                        if(status != 412 && status != 304) {
                           HttpUtilities.processUnexpectedStatusCode(ioe);
                        } else {
                           localModified.setValue(Boolean.valueOf(true));
                           HttpUtilities.processUnexpectedStatusCode(ioe);
                        }
                     } else {
                        localModified.setValue(Boolean.valueOf(true));
                        HttpUtilities.processUnexpectedStatusCode(ioe);
                     }
                  } else {
                     ioe.close();
                     retval.setValue(Boolean.valueOf(true));
                  }
               } catch (StorageException var7) {
                  HttpUtilities.translateWebException(var7);
               }

               return null;
            }
         });
         unused.setValue((Boolean)localModified.getValue());
         return ((Boolean)retval.getValue()).booleanValue();
      }
   }

   URI constructBlobUri(String blobName) {
      ResourceUriComponents uriComponents = new ResourceUriComponents(this.getAccountName(), this.getName(), blobName);
      return HttpUtilities.createRequestUri(this.getBaseUri(), this.isUsePathStyleUris(), this.getAccountName(), this.getName(), blobName, (TimeSpan)null, new NameValueCollection(), uriComponents);
   }

   private URI removeQueryParams(URI blobUri) throws URISyntaxException {
      String uri = blobUri.toString();
      int pos = uri.indexOf(63);
      return pos < 0?blobUri:new URI(uri.substring(0, pos));
   }

   public Iterator listBlobs() {
      return this.listBlobs((String)null, false);
   }

   public Iterator listBlobs(String prefix, boolean combineCommonPrefixes, int maxResults) throws StorageException {
      if(maxResults <= 0) {
         throw new IllegalArgumentException("maxResults should be positive value.");
      } else {
         ListBlobsResult all = new ListBlobsResult(new ArrayList(), new ArrayList(), "");
         String marker = "";
         String delimiter = combineCommonPrefixes?"/":Utilities.emptyString();
         List blobs = all.getBlobsProperties();

         do {
            ListBlobsResult partResult = this.listBlobsImpl(prefix, marker, delimiter, maxResults);
            marker = partResult.getNextMarker();
            blobs.addAll(partResult.getBlobsProperties());
            all.getCommonPrefixs().addAll(partResult.getCommonPrefixs());
         } while(marker != null);

         return blobs.iterator();
      }
   }

   public Iterator listBlobs(String prefix, boolean combineCommonPrefixes) throws StorageException {
      boolean maxResults = true;

      try {
         return this.listBlobs(prefix, combineCommonPrefixes, 100);
      } catch (StorageException var5) {
         throw HttpUtilities.translateWebException(var5);
      }
   }

   private ListBlobsResult listBlobsImpl(final String prefix, final String fromMarker, final String delimiter, final int maxResults) throws StorageException {
      final OutParameter result = new OutParameter();
      this.getRetryPolicy().execute(new Callable() {
         public Object call() throws Exception {
            NameValueCollection queryParameters = BlobContainerRest.this.createRequestUriForListing(prefix, fromMarker, delimiter, maxResults);
            ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null);
            queryParameters.put("restype", "container");
            URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null, BlobContainerRest.this.getTimeout(), queryParameters, uriComponents, BlobContainerRest.this.credentials);
            HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "GET", BlobContainerRest.this.getTimeout());
            request.setHeader("x-ms-version", "2009-09-19");
            BlobContainerRest.this.credentials.signRequest(request, uriComponents);
            HttpWebResponse response = HttpUtilities.getResponse(request);
            if(response.getStatusCode() == 200) {
               result.setValue(BlobContainerRest.this.parseBlobFromResponse(response.getStream()));
               response.close();
            } else {
               XmlUtil.load(response.getStream());
               HttpUtilities.processUnexpectedStatusCode(response);
            }

            return null;
         }
      });
      return (ListBlobsResult)result.getValue();
   }

   private ListBlobsResult parseBlobFromResponse(InputStream stream) throws StorageServerException {
      ArrayList blobs = new ArrayList();
      ArrayList commonPrefixes = new ArrayList();
      String nextMarker = null;
      Document document = XmlUtil.load(stream);
      List xmlNodes = document.selectNodes(XPathQueryHelper.CommonPrefixQuery);
      Iterator nextMarkerNode = xmlNodes.iterator();

      Element blobNode;
      while(nextMarkerNode.hasNext()) {
         blobNode = (Element)nextMarkerNode.next();
         String propNode = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Name", false);
         commonPrefixes.add(propNode);
      }

      xmlNodes = document.getRootElement().element("Blobs").elements("Blob");
      nextMarkerNode = xmlNodes.iterator();

      while(nextMarkerNode.hasNext()) {
         blobNode = (Element)nextMarkerNode.next();
         Element propNode1 = blobNode.element("Properties");
         if(propNode1 == null) {
            blobs.add(this.parseBlobInfo(blobNode));
         } else {
            blobs.add(this.parseBlobInfo2(blobNode, propNode1));
         }
      }

      Element nextMarkerNode1 = (Element)document.selectSingleNode(XPathQueryHelper.NextMarkerQuery);
      if(nextMarkerNode1 != null && nextMarkerNode1.hasContent()) {
         nextMarker = nextMarkerNode1.getStringValue();
      }

      return new ListBlobsResult(blobs, commonPrefixes, nextMarker);
   }

   private BlobProperties parseBlobInfo(Element blobNode) {
      String blobUrl = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Url", true);
      String blobName = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Name", true);
      Timestamp lastModified = XPathQueryHelper.loadSingleChildDateTimeValue(blobNode, "LastModified", false);
      String eTag = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Etag", false);
      String contentType = XPathQueryHelper.loadSingleChildStringValue(blobNode, "ContentType", false);
      String contentEncoding = XPathQueryHelper.loadSingleChildStringValue(blobNode, "ContentEncoding", false);
      String contentLanguage = XPathQueryHelper.loadSingleChildStringValue(blobNode, "ContentLanguage", false);
      Long blobSize = XPathQueryHelper.loadSingleChildLongValue(blobNode, "Size", false);
      BlobProperties properties = new BlobProperties(blobName);
      if(lastModified != null) {
         properties.setLastModifiedTime(lastModified);
      }

      properties.setContentType(contentType);
      properties.setContentEncoding(contentEncoding);
      properties.setContentLanguage(contentLanguage);
      properties.setETag(eTag);
      properties.setContentLength(blobSize.longValue());
      blobUrl = Utilities.fixRootContainer(blobUrl);
      blobUrl = blobUrl.replaceAll(" ", "%20");
      properties.setUri(URI.create(blobUrl));
      return properties;
   }

   private BlobProperties parseBlobInfo2(Element blobNode, Element propNode) {
      String blobUrl = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Url", true);
      String blobName = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Name", true);
      String snapshot = XPathQueryHelper.loadSingleChildStringValue(blobNode, "Snapshot", false);
      String blobType = XPathQueryHelper.loadSingleChildStringValue(propNode, "BlobType", false);
      String leaseStatus = XPathQueryHelper.loadSingleChildStringValue(propNode, "LeaseStatus", false);
      Timestamp lastModified = XPathQueryHelper.loadSingleChildDateTimeValue(propNode, "Last-Modified", false);
      String eTag = XPathQueryHelper.loadSingleChildStringValue(propNode, "Etag", false);
      String contentType = XPathQueryHelper.loadSingleChildStringValue(propNode, "Content-Type", false);
      String contentEncoding = XPathQueryHelper.loadSingleChildStringValue(propNode, "Content-Encoding", false);
      String contentLanguage = XPathQueryHelper.loadSingleChildStringValue(propNode, "Content-Language", false);
      Long blobSize = XPathQueryHelper.loadSingleChildLongValue(propNode, "Content-Length", false);
      String contentMd5 = XPathQueryHelper.loadSingleChildStringValue(propNode, "Content-MD5", false);
      BlobProperties properties = new BlobProperties(blobName);
      if(lastModified != null) {
         properties.setLastModifiedTime(lastModified);
      }

      properties.setContentType(contentType);
      properties.setContentEncoding(contentEncoding);
      properties.setContentLanguage(contentLanguage);
      properties.setETag(eTag);
      properties.setContentMD5(contentMd5);
      if(blobSize != null) {
         properties.setContentLength(blobSize.longValue());
      }

      blobUrl = Utilities.fixRootContainer(blobUrl);
      blobUrl = blobUrl.replaceAll(" ", "%20");
      properties.setUri(URI.create(blobUrl));
      properties.setBlobType(BlobType.parse(blobType));
      properties.setLeaseStatus(LeaseStatus.parse(leaseStatus));
      if(snapshot != null) {
         try {
            properties.setSnapshot(Utilities.tryGetDateTimeFromTableEntry(snapshot));
         } catch (ParseException var17) {
            ;
         }
      }

      return properties;
   }

   private NameValueCollection createRequestUriForListing(String prefix, String fromMarker, String delimiter, int maxResults) {
      NameValueCollection queryParams = new NameValueCollection();
      queryParams.put("comp", "list");
      if(!Utilities.isNullOrEmpty(prefix)) {
         queryParams.put("prefix", prefix);
      }

      if(!Utilities.isNullOrEmpty(fromMarker)) {
         queryParams.put("marker", fromMarker);
      }

      if(!Utilities.isNullOrEmpty(delimiter)) {
         queryParams.put("delimiter", delimiter);
      }

      queryParams.put("maxresults", Integer.toString(maxResults));
      return queryParams;
   }

   public IBlob getBlobReference(String name) {
      BlockBlob blob = new BlockBlob(this, name);
      IBlobProperties properties = blob.getProperties();
      return (IBlob)(properties == null?blob:(BlobType.BlockBlob.equals(properties.getBlobType())?blob:this.getPageBlobReference(name)));
   }

   public IBlockBlob getBlockBlobReference(String name) {
      return new BlockBlob(this, name);
   }

   public IPageBlob getPageBlobReference(String name) {
      return new PageBlob(this, name);
   }

   public boolean copyBlob(String destContainer, String destBlobName, String sourceBlobName) throws StorageException {
      return this.copyBlobImpl(destContainer, destBlobName, sourceBlobName, (NameValueCollection)null, (IBlobConstraints)null);
   }

   public boolean copyBlob(String destContainer, String destBlobName, String sourceBlobName, NameValueCollection metadata, IBlobConstraints constraints) throws StorageException {
      return this.copyBlobImpl(destContainer, destBlobName, sourceBlobName, metadata, constraints);
   }

   private boolean copyBlobImpl(String destContainer, String destBlobName, final String sourceBlobName, final NameValueCollection metadata, final IBlobConstraints constraints) throws StorageException {
      if(Utilities.isNullOrEmpty(sourceBlobName)) {
         throw new IllegalArgumentException("Source blob name cannot be null or empty!");
      } else {
         final String container = Utilities.isNullOrEmpty(destContainer)?this.getName():destContainer;
         final String blob = Utilities.isNullOrEmpty(destBlobName)?sourceBlobName:destBlobName;
         if(container.equals(this.getName()) && blob.equals(sourceBlobName)) {
            throw new IllegalArgumentException("Destnation blob and source blob could not be the same.");
         } else {
            final OutParameter retval = new OutParameter(Boolean.valueOf(false));
            this.getRetryPolicy().execute(new Callable() {
               public Object call() throws Exception {
                  ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), container, blob);
                  URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), container, blob, BlobContainerRest.this.getTimeout(), (NameValueCollection)null, uriComponents);
                  HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "PUT", BlobContainerRest.this.getTimeout());
                  request.addHeader("x-ms-version", "2009-07-17");
                  String blobName = BlobContainerRest.this.createCopySourceHeaderValue(sourceBlobName).replaceAll(" ", "%20");
                  request.addHeader("x-ms-copy-source", blobName);
                  BlobContainerRest.this.addMoreConstraints(constraints, request);
                  if(metadata != null) {
                     HttpUtilities.addMetadataHeaders(request, metadata);
                  }

                  BlobContainerRest.this.credentials.signRequest(request, uriComponents);
                  HttpWebResponse response = HttpUtilities.getResponse(request);
                  int statusCode = response.getStatusCode();
                  if(statusCode == 201) {
                     response.close();
                     retval.setValue(Boolean.valueOf(true));
                  } else {
                     retval.setValue(Boolean.valueOf(false));
                     HttpUtilities.processUnexpectedStatusCode(response);
                  }

                  return retval;
               }
            });
            return ((Boolean)retval.getValue()).booleanValue();
         }
      }
   }

   private void addMoreConstraints(IBlobConstraints constraints, HttpRequest request) {
      if(constraints != null) {
         List headers = constraints.getConstraints();
         if(headers != null && !headers.isEmpty()) {
            Iterator var5 = headers.iterator();

            while(var5.hasNext()) {
               BasicHeader header = (BasicHeader)var5.next();
               request.addHeader(header);
            }
         }
      }

   }

   private String createCopySourceHeaderValue(String sourceBlobName) {
      return String.format("/%s/%s/%s", new Object[]{this.getAccountName(), this.getName(), sourceBlobName});
   }

   public void clearSharedAccessUrl() {
      this.shareAccessUrl = null;
      if(this.credentials instanceof SharedKeyCredentialsWrapper) {
         SharedKeyCredentialsWrapper warpper = (SharedKeyCredentialsWrapper)this.credentials;
         this.credentials = warpper.getCredentials();
      }

   }

   public void useSharedAccessUrl(ISharedAccessUrl url) {
      if(url == null) {
         throw new IllegalArgumentException("Share access url invalid");
      } else {
         this.shareAccessUrl = url;
         this.credentials = new SharedKeyCredentialsWrapper(this.credentials, this.shareAccessUrl, this);
      }
   }

   public ISharedAccessUrl getShareAccessUrl() {
      return this.shareAccessUrl;
   }

   public String leaseBlob(IBlobProperties blobProperties, final LeaseMode mode, final NameValueCollection headerParameters) throws StorageException {
      String container = this.getName();
      if(container.equals("$root")) {
         container = "";
      }

      String blobName = blobProperties.getName();
      NameValueCollection queryParams = new NameValueCollection();
      queryParams.put("comp", "lease");
      final ResourceUriComponents uriComponents = new ResourceUriComponents(this.getAccountName(), container, blobName);
      final URI blobUri = HttpUtilities.createRequestUri(this.getBaseUri(), this.isUsePathStyleUris(), this.getAccountName(), container, blobName, (TimeSpan)null, queryParams, uriComponents);
      String id = (String)this.getRetryPolicy().execute(new Callable() {
         public String call() throws Exception {
            HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(blobUri, "PUT", (TimeSpan)null);
            request.addHeader("x-ms-version", "2009-09-19");
            request.addHeader("x-ms-lease-action", mode.getLiteral());
            BlobContainerRest.this.appendHeaders(request, headerParameters);
            BlobContainerRest.this.credentials.signRequest(request, uriComponents);

            try {
               HttpWebResponse ioe = HttpUtilities.getResponse(request);
               int status = ioe.getStatusCode();
               if(status == 201 || status == 200 || status == 202) {
                  String result = ioe.getHeader("x-ms-lease-id");
                  ioe.close();
                  return result;
               }

               ioe.close();
            } catch (Exception var5) {
               HttpUtilities.translateWebException(var5);
            }

            return null;
         }
      });
      return id;
   }

   public IPageBlob createPageBlob(IBlobProperties blobProperties, final int size, final NameValueCollection headerParameters) throws StorageException {
      String blobName = blobProperties.getName();
      if(blobName != null && !blobName.equals("")) {
         String container = this.getName();
         if(container.equals("$root")) {
            container = "";
         }

         final ResourceUriComponents uriComponents = new ResourceUriComponents(this.getAccountName(), container, blobName);
         final URI blobUri = HttpUtilities.createRequestUri(this.getBaseUri(), this.isUsePathStyleUris(), this.getAccountName(), container, blobName, (TimeSpan)null, new NameValueCollection(), uriComponents, this.getCredentials());
         this.getRetryPolicy().execute(new Callable() {
            public Boolean call() throws Exception {
               HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(blobUri, "PUT", (TimeSpan)null);
               request.addHeader("x-ms-version", "2009-09-19");
               request.addHeader("x-ms-blob-type", BlobType.PageBlob.getLiteral());
               request.addHeader("x-ms-blob-content-length", String.valueOf(size));
               BlobContainerRest.this.appendHeaders(request, headerParameters);
               BlobContainerRest.this.credentials.signRequest(request, uriComponents);

               try {
                  HttpWebResponse ioe = HttpUtilities.getResponse(request);
                  int status = ioe.getStatusCode();
                  if(status == 201) {
                     ioe.close();
                     return Boolean.TRUE;
                  }

                  ioe.close();
               } catch (Exception var4) {
                  HttpUtilities.translateWebException(var4);
               }

               return Boolean.FALSE;
            }
         });
         return this.getPageBlobReference(blobName);
      } else {
         throw new IllegalArgumentException("Blob name is empty.");
      }
   }

   public void setMetadata(final NameValueCollection metadata) {
      try {
         this.getRetryPolicy().execute(new Callable() {
            public Object call() throws Exception {
               ResourceUriComponents uriComponents = new ResourceUriComponents(BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null);
               NameValueCollection queryParams = new NameValueCollection();
               queryParams.put("comp", "metadata");
               queryParams.put("restype", "container");
               URI uri = HttpUtilities.createRequestUri(BlobContainerRest.this.getBaseUri(), BlobContainerRest.this.isUsePathStyleUris(), BlobContainerRest.this.getAccountName(), BlobContainerRest.this.getName(), (String)null, BlobContainerRest.this.getTimeout(), queryParams, uriComponents);
               HttpRequest request = HttpUtilities.createHttpRequestWithCommonHeaders(uri, "PUT", BlobContainerRest.this.getTimeout());
               if(metadata != null) {
                  HttpUtilities.addMetadataHeaders(request, metadata);
               }

               request.addHeader("x-ms-version", "2009-07-17");
               BlobContainerRest.this.credentials.signRequest(request, uriComponents);

               try {
                  HttpWebResponse we = HttpUtilities.getResponse(request);
                  if(we.getStatusCode() == 200) {
                     we.close();
                     return Boolean.valueOf(true);
                  } else {
                     HttpUtilities.processUnexpectedStatusCode(we);
                     return Boolean.valueOf(false);
                  }
               } catch (StorageException var6) {
                  throw HttpUtilities.translateWebException(var6);
               }
            }
         });
      } catch (StorageException var3) {
         throw var3;
      }
   }

   public IBlockBlob updateBlockBlob(IBlobProperties blobProperties, IBlobContents blobContents) throws StorageException {
      IBlockBlob blob = null;
      boolean blobExist = this.isBlobExist(blobProperties.getName());
      if(!blobExist) {
         throw new StorageException("The blob does not exist!");
      } else {
         boolean updateBlob = this.createOrUpdateBlockBlob(blobProperties, blobContents, true);
         if(updateBlob) {
            blob = this.getBlockBlobReference(blobProperties.getName());
         }

         return blob;
      }
   }

   public IBlockBlob createBlockBlob(IBlobProperties blobProperties, IBlobContents blobContents) throws StorageException {
      IBlockBlob blob = null;
      boolean blobExist = this.isBlobExist(blobProperties.getName());
      if(blobExist) {
         throw new StorageException("The blob already exists!");
      } else {
         boolean createBlob = this.createOrUpdateBlockBlob(blobProperties, blobContents, false);
         if(createBlob) {
            blob = this.getBlockBlobReference(blobProperties.getName());
         }

         return blob;
      }
   }

   void appendHeaders(HttpRequest request, NameValueCollection headerParameters) {
      if(headerParameters != null && headerParameters.size() != 0) {
         Iterator var4 = headerParameters.keySet().iterator();

         while(var4.hasNext()) {
            Object key = var4.next();
            String value = headerParameters.getMultipleValuesAsString((String)key);
            if(value != null) {
               request.addHeader(key.toString(), value);
            }
         }

      }
   }

   SharedKeyCredentials getCredentials() {
      return this.credentials;
   }
}
