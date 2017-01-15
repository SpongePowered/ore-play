// TODO: need to find a way to include this into ore
importScripts('https://lergin.de/bower_components/sw-toolbox/sw-toolbox.js');

function networkOnlyError(request, values, options) {
  return toolbox.networkOnly(request, values, options).catch((err) => {
    return toolbox.cacheOnly(new Request('/offline'));
  });
}

function networkFirstError(request, values, options) {
  return toolbox.networkFirst(request, values, options).catch((err) => {
    return toolbox.cacheOnly(new Request('/offline'));
  });
}

// pre cache all urls that are needed for the main and offline page
var preCache = [
    '/',
    '/offline',
    '/assets/stylesheets/fontawesome/font-awesome.css',
    '/assets/bootstrap/css/bootstrap.min.css',
    '/assets/bootstrap/css/bootstrap.min.css',
    '/assets/bootstrap/js/bootstrap.min.js',
    '/assets/javascripts/svg.js',
    '/assets/javascripts/main.js',
    '/assets/javascripts/jquery-2.2.1.min.js',
    '/assets/stylesheets/main.css',
    '/assets/images/ore-dark.png',
    '/assets/images/ore-desc.png',
    '/assets/images/spongie-mark.svg'
];

toolbox.precache(preCache);

// serve every get request from the cache if no connection is available
toolbox.router.get( '(.*)', networkFirstError );


// deaktivate cache for the api and statusz
toolbox.router.get( '/statusz', toolbox.networkOnly );
toolbox.router.get( '/api/(.*)', toolbox.networkOnly );

// downloads are not possible while offline and shouldn't be cached
toolbox.router.get( '/:author/:slug/versions/:version/download', networkOnlyError );
toolbox.router.get( '/:author/:slug/versions/:version/signature', networkOnlyError );
toolbox.router.get( '/:author/:slug/versions/:version/jar', networkOnlyError );

// post requests will not work offline
toolbox.router.post('(.*)', toolbox.networkOnlyError);

// deaktivate cache for some fo the admin routes
toolbox.router.get('/admin/seed', toolbox.networkOnlyError);
toolbox.router.get('/admin/flags/(.*)', toolbox.networkOnlyError);

// login / logout is not possible while offline
toolbox.router.get('/logout', toolbox.networkOnlyError);
toolbox.router.get('/login', toolbox.networkOnlyError);
toolbox.router.get('/signup', toolbox.networkOnlyError);