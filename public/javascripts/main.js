//=====> Nonce Variables

var csrf = null;


//=====> Constants

var KEY_ENTER = 13;


//=====> Clipboard Manager

var clipboardManager = new ClipboardJS('.copy-url');
clipboardManager.on('success', function(e) {
    var element = $('.btn-download').tooltip({title: 'Copied!', placement: 'bottom', trigger: 'manual'}).tooltip('show');
    setTimeout(function () {
        element.tooltip('destroy');
    }, 2200);
});


//=====> Highlight JS

hljs.initHighlightingOnLoad();


//=====> Helper Methods
// todo: check what's really needed here

function sanitize(html) {
    return $('<textarea>').html(html).text();
}

function decodeHtml(html) {
    // lol
    return $('<textarea>').html(html).val();
}

function go(str) {
    window.location = decodeHtml(str);
}

function clearUnread(e) {
    e.find('.unread').remove();
    if (!$('.user-dropdown .unread').length) $('.unread').remove();
}

function slugify(name) {
    return name.trim().replace(/ +/g, ' ').replace(/ /g, '-');
}

function toggleSpinner(e) {
    return e.toggleClass('fa-spinner').toggleClass('fa-spin');
}


//=====> jQuery Doc Ready

$(function() {
    // Initialize tooltips
    $('[data-toggle="tooltip"]').tooltip({
        container: "body",
        delay: { "show": 500 }
    });

    // Set action for go back button
    $(".link-go-back").click(function (e) {
        e.preventDefault();

        window.history.back();
    });
});


//=====> Page Anchor Fix

var scrollToAnchor = function (anchor) {
    var target = $("a" + anchor);

    if (target.length) {
        $('html,body').animate({
            scrollTop: target.offset().top - ($("#topbar").height() + 10)
        }, 1);
    }
};

$(window).load(function () {
    return scrollToAnchor(window.location.hash);
});

$("a[href^='#']").click(function () {
    window.location.replace(window.location.toString().split("#")[0] + $(this).attr("href"));

    return scrollToAnchor(this.hash);
});


//=====> Service Worker

// The service worker has been removed in commit 9ab90b5f4a5728587fc08176e316edbe88dfce9e.
// This code ensures that the service worker is removed from the browser.

if (window.navigator && navigator.serviceWorker) {
    if ('getRegistrations' in navigator.serviceWorker) {
        navigator.serviceWorker.getRegistrations().then(function (registrations) {
            registrations.forEach(function (registration) {
                registration.unregister();
            })
        })
    } else if ('getRegistration' in navigator.serviceWorker) {
        navigator.serviceWorker.getRegistration().then(function (registration) {
            registration.unregister();
        })
    }
}
